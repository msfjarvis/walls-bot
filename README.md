# walls-bot
Telegram bot for my wallpapers collection, in Kotlin

Requires my custom fork of [seik/kotlin-telegram-bot](https://github.com/seik/kotlin-telegram-bot) which can be found [here](https://github.com/MSF-Jarvis/kotlin-telegram-bot/tree/msf/parse_mode_for_photos).

### To build

- Clone my fork of the kotlin-telegram-bot library and copy the `telegram` folder from there into the root of this repo
- Run `./gradlew shadowJar` to generate a full jar at `build/libs/`
- Copy `config.prop.sample` as `config.prop` and edit with the necessary credentials
- Start the bot: `java -jar build/libs/wallsbot-0.1-all.jar`
