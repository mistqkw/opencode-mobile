package app.ocmobile.ui.components

/**
 * cl0udly greetings on home: cloud puns, developer jokes and short nods
 * to films and games (retold in our own words, no quotes longer than a couple of words).
 * The first line has the name if there is one ({name}), the second is a question or a tease.
 * A new one on every visit.
 */
object Greetings {
    private val any = listOf(
        "Hi, {name}." to "Cloudy with a chance of code.",
        "Forecast for today:" to "100% chance of shipping.",
        "Hey, {name}." to "It's not a bug, it's a weather event.",
        "Hello there, {name}." to "What are we making?",
        "Wake up, {name}." to "The terminal has you.",
        "May the source" to "be with you, {name}.",
        "To deploy" to "or not to deploy?",
        "I'll be back…" to "with a pull request.",
        "Works on my cloud." to "Let's make it work on yours.",
        "Ready, player {name}?" to "Press start.",
        "Keep calm" to "and git commit.",
        "One does not simply" to "push to main.",
        "Winter is coming." to "So is the deadline.",
        "Do or do not." to "There is no try-catch.",
        "404:" to "worries not found.",
        "Houston," to "we have no problems. Yet.",
        "Talk is cheap." to "Show me the prompt.",
        "Floating around, {name}." to "What's up?",
        "Feeling fluffy today." to "You?",
        "Light drizzle of ideas." to "Want one?",
        "Cumulus. Nimbus." to "cl0udly.",
        "Brewing a storm" to "of features.",
        "The cloud is just someone else's phone." to "This time it's yours.",
        "Hello, world." to "Hello, {name}.",
        "Let's build something" to "the rubber duck would be proud of.",
        "Vibes: immaculate." to "Tests: let's find out.",
        "Your move, {name}." to "I'm all ears. Well, all eyes.",
        "Achievement unlocked:" to "opened the app.",
        "Loading creativity…" to "99%. Your turn.",
        "It's dangerous to code alone." to "Take me with you.",
        "The cake is not a lie." to "The docs might be.",
        "Elementary, my dear {name}." to "What's the case?",
        "We're gonna need" to "a bigger repo.",
        "Live long" to "and push often.",
        "I see dead code." to "Want me to clean it up?",
        "Why so serious?" to "It's just a refactor.",
        "There's no place" to "like 127.0.0.1.",
        "Hakuna matata." to "No merge conflicts.",
        "Wingardium Leviosa…" to "the build is floating.",
        "Keep your friends close" to "and your backups closer.",
        "All your base" to "are belong to you, {name}.",
        "Have you tried turning it off" to "and on again?",
        "It's a-me, cl0udly." to "Let's-a go!",
        "Hey, listen!" to "I've got time for a task.",
        "Do a barrel roll," to "then tell me what to do.",
        "Snake? Snake?!" to "Oh, it's just Python.",
        "The floor is lava." to "The cloud is fine.",
        "Sky's clear, {name}." to "Good day for a deploy.",
        "Partly cloudy," to "fully ready.",
        "No rain today." to "Unless you want a waterfall chart.",
        "Somewhere over the rainbow," to "a build turned green.",
        "First rule of cl0udly:" to "you always talk about your task.",
        "Is this the real life?" to "Is this just a prompt?",
        "Hi, {name}!" to "I promise I'm not a ghost.",
        "I came, I saw," to "I compiled.",
        "Not all who wander are lost." to "Some are just reading the docs.",
        "Up, up and away," to "{name}. Where to?",
        "Ground control to {name}:" to "ready when you are.",
        "Insert coin" to "to continue. Or just type.",
        "Big brain time, {name}." to "What's the plan?",
    )
    private val codeOnly = listOf(
        "Hi, {name}." to "What are we building today?",
        "git status:" to "ready for your next idea.",
        "Compiling thoughts…" to "Done. What's the task?",
        "Semicolons ready." to "Brackets balanced. Go.",
    )

    /** A pair of greeting lines; [code] is code mode, which adds coder phrases. */
    fun pick(name: String, code: Boolean): String {
        val pool = if (code) any + codeOnly + codeOnly else any
        val (a, b) = pool.random()
        val who = name.ifBlank { "friend" }
        return a.replace("{name}", who) + "\n" + b.replace("{name}", who)
    }
}
