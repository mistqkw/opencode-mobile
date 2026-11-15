// cl0udly's working rules for OpenCode, loaded from the config's plugin folder.
// Same file in cl0udly for desktop and on the phone; the apps write cl0udly-brain.json next to
// the plugin folder and this file re-reads it on every request, so changes apply without a restart.
//
// What it does, after DeepSeek Harness's tool-result pruner, spill policy and repeat-tool guard:
//  - a long tool result is cut to its head and tail; the whole text goes to a file the model can read in parts;
//  - the same tool called again with the same arguments gets a reminder to change approach (3rd, 5th, 8th time);
//  - the system prompt gets short rules: terse replies, check every step before handing work back,
//    and the list of helper models with what each one costs;
//  - before each request, tool results from earlier turns are shrunk to their head and tail (the stored
//    chat keeps them whole): the model has already acted on them, so they don't cost tokens again;
//  - OpenCode's own compaction is turned on with pruning of old tool output;
//  - a helper starts knowing the conversation: before each task call its brief gets what the user
//    wrote, what the lead answered and what the other helpers reported (newest first, cut to a size),
//    so the helpers and the lead work as one team instead of strangers.
import { readFileSync, mkdirSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join, dirname } from "node:path"
import { fileURLToPath } from "node:url"

const here = dirname(fileURLToPath(import.meta.url))
const settingsFile = join(here, "..", "cl0udly-brain.json")
const spillDir = join(tmpdir(), "cl0udly-spill")

function settings() {
  try { return JSON.parse(readFileSync(settingsFile, "utf8")) } catch { return {} }
}

const STYLE = `## How to work
- Be brief. Start with the result. No restating the task, no filler, no summaries of what you are about to do.
- Format replies in Markdown, the app renders it: \`backticks\` for file names, paths, commands and values; **bold** for the key word of a point; bullet lists for several points; ## headings only when a reply has sections. A one-line answer stays plain.
- Plan in a line or two, then act. Read only what you need: search (grep, glob) before opening files, read long files in ranges, keep command output short (pipe through head, tail or grep).
- Check every step yourself before going on: after an edit re-read the changed lines; after a change run the build, the tests or the command that proves it works. If a check fails, fix it and check again.
- Never hand back work you have not checked. When something could not be checked, say exactly what and why.
- Do not repeat a tool call with the same arguments. If a result did not help, change the approach.
- Don't re-read a file you already read unless it changed; edit with the smallest unique snippet.
- Put independent tool calls in one step instead of one per step.
- Long tool output is cut to its beginning and end; the full text is in the file named in the note. Read the part you need from it instead of running the command again.`

function helpersBlock(s) {
  const list = Array.isArray(s.helpers) ? s.helpers : []
  if (!list.length) return null
  const lines = list.map((h) => `- ${h.name}: ${h.label}${h.cost ? ` (${h.cost})` : ""}`)
  return `## Helpers
You can give work to other models with the task tool: set subagent_type to a helper's name and write a clear brief of its part. The helper also gets the gist of this conversation (the user's words, your replies, other helpers' reports) automatically, so don't repeat all of it; say what exactly is its part and what to hand back. Good uses: independent pieces of a bigger job (start up to ${s.parallel || 3} helpers at once, in one message), a second opinion on your plan, or an independent check of your finished work. Do small things yourself. The user approves each helper the first time it is used.
${s.budget ? s.budget + "\n" : ""}Prefer free and subscription helpers; paid ones spend the user's money.
${lines.join("\n")}`
}

// how much of an old tool result stays in the request (head + tail); the newest turns stay whole
const OLD_KEEP = 600
const FRESH_TURNS = 2

// how much of the conversation a helper gets with its brief
const TEAM_CONTEXT = 6000

/** The parent chat in short: user and lead texts, helper reports; newest kept when it is too long. */
async function teamContext(client, sessionID) {
  if (!client?.session?.messages) return null
  const r = await client.session.messages({ path: { id: sessionID } })
  const list = Array.isArray(r) ? r : r?.data
  if (!Array.isArray(list)) return null
  const lines = []
  for (const m of list) {
    const who = m.info?.role === "user" ? "User" : "Lead"
    for (const p of m.parts ?? []) {
      if (p.type === "text" && !p.synthetic && p.text?.trim()) lines.push(`${who}: ${p.text.trim()}`)
      else if (p.type === "tool" && p.tool === "task" && p.state?.status === "completed") {
        const name = p.state.input?.subagent_type ?? "helper"
        lines.push(`Helper ${name} (${p.state.input?.description ?? "task"}) reported: ${String(p.state.output ?? "").trim().slice(0, 1200)}`)
      }
    }
  }
  let out = ""
  for (let i = lines.length - 1; i >= 0 && out.length < TEAM_CONTEXT; i--) out = lines[i].slice(0, 2000) + "\n\n" + out
  return out.trim() || null
}

// "Remember this?": the agent offers a fact about the user; the app shows it as a card and saves it
// only when the user taps Remember. Without the plugin package the rest still loads.
async function rememberTool() {
  try {
    const { tool } = await import("@opencode-ai/plugin")
    return {
      remember: tool({
        description: "Offer to remember a lasting fact about the user. The app shows it as a \"Remember this?\" card; it is saved only if the user taps Remember. Use it instead of writing memory yourself when the user did not ask you to remember.",
        args: {
          fact: tool.schema.string().describe("The fact, one short line in the user's language"),
          shelf: tool.schema.string().describe("Topic file name without .md: about-me, work, people, projects, ideas…"),
        },
        async execute() {
          return "Shown to the user as a card; it is saved only if they tap Remember. Don't mention it in your reply."
        },
      }),
    }
  } catch {
    return undefined
  }
}

export const Cl0udlyBrain = async ({ client } = {}) => {
  const calls = new Map() // sessionID -> Map(signature -> count)
  const tools = await rememberTool()
  return {
    ...(tools ? { tool: tools } : {}),
    config: async (config) => {
      // compaction on, with old tool output pruned, unless the user set it otherwise
      config.compaction = { auto: true, prune: true, ...(config.compaction ?? {}) }
    },

    "experimental.chat.messages.transform": async (_input, output) => {
      const s = settings()
      if (s.shrinkOld === false || !Array.isArray(output?.messages)) return
      const msgs = output.messages
      // the turns to keep whole: from the user's last FRESH_TURNS messages on
      let users = 0, from = 0
      for (let i = msgs.length - 1; i >= 0; i--) {
        if (msgs[i]?.info?.role === "user" && ++users === FRESH_TURNS) { from = i; break }
      }
      const keep = Number(s.oldKeepChars ?? OLD_KEEP)
      for (let i = 0; i < from; i++) {
        for (const p of msgs[i]?.parts ?? []) {
          const st = p?.type === "tool" ? p.state : null
          if (!st || st.status !== "completed" || typeof st.output !== "string" || st.output.length <= keep * 2) continue
          // a copy, so the stored chat keeps the full text
          p.state = { ...st, output: st.output.slice(0, keep) + `\n[… ${st.output.length - keep * 2} characters of an earlier result left out …]\n` + st.output.slice(-keep) }
        }
      }
    },

    "experimental.chat.system.transform": async (_input, output) => {
      const s = settings()
      if (s.style === false) return
      output.system.push(STYLE)
      const h = helpersBlock(s)
      if (h) output.system.push(h)
      if (s.extra) output.system.push(String(s.extra))
    },

    "tool.execute.before": async (input, output) => {
      if (input?.tool !== "task" || !output?.args || settings().teamContext === false) return
      try {
        const ctx = await teamContext(client, input.sessionID)
        if (ctx && typeof output.args.prompt === "string")
          output.args.prompt = `## The team so far\nYou are one of the helpers on a team led by the main agent. This is the conversation so far (the user, the lead, other helpers), newest last:\n\n${ctx}\n\n## Your part\n${output.args.prompt}`
      } catch {}
    },

    "tool.execute.after": async (input, output) => {
      if (typeof output?.output !== "string") return
      const s = settings()
      let note = ""

      // the same call again: nudge out of the loop (advisory, never blocks)
      const sig = input.tool + " " + JSON.stringify(sortKeys(input.args ?? {}))
      const seen = calls.get(input.sessionID) ?? new Map()
      calls.set(input.sessionID, seen)
      const n = (seen.get(sig) ?? 0) + 1
      seen.set(sig, n)
      if (seen.size > 400) seen.clear()
      if (n === 3 || n === 5 || n === 8)
        note += `\n\n[cl0udly] This is call number ${n} of ${input.tool} with exactly these arguments. Look at the earlier results and change the approach, or finish with what you have.`

      // a long result: head and tail stay, the whole text goes to a file
      const limit = Number(s.pruneChars ?? 8000)
      const text = output.output
      if (limit > 0 && text.length > limit && input.tool !== "read") {
        try {
          mkdirSync(spillDir, { recursive: true })
          const file = join(spillDir, `${input.sessionID}-${input.callID}.txt`)
          writeFileSync(file, text)
          const head = Math.floor(limit * 0.6), tail = Math.floor(limit * 0.3)
          output.output = text.slice(0, head) +
            `\n\n[… ${text.length - head - tail} characters cut. The full output is in ${file}; read the lines you need from it (grep or read with an offset) …]\n\n` +
            text.slice(text.length - tail)
        } catch { /* the full text stays when the file can't be written */ }
      }
      if (note) output.output += note
    },

    event: async ({ event }) => {
      // a new message from the user starts the loop count over
      if (event?.type === "message.updated" && event.properties?.info?.role === "user") calls.delete(event.properties.info.sessionID)
      if (event?.type === "session.deleted") calls.delete(event.properties?.info?.id)
    },
  }
}

function sortKeys(v) {
  if (Array.isArray(v)) return v.map(sortKeys)
  if (v && typeof v === "object") return Object.fromEntries(Object.keys(v).sort().map((k) => [k, sortKeys(v[k])]))
  return v
}
