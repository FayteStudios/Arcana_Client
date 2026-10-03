# Arcana Client

A custom client for [Salem](http://salemthegame.com), with the Almanac (an in-game book of everything you find, built from the wiki), crafting from recipes with a shopping list, your own action bars and macros, timers, a world map with markers, tidier containers, and much more.

## Playing

Download the latest zip from [Releases](https://github.com/FayteStudios/Arcana_Client/releases), unzip it anywhere and run `Arcana.bat`. The README inside explains what you need (Salem installed with the official launcher, and Java 8).

Feedback: in the game, Options > Help & feedback, or the Arcana thread on the Salem forum.

## Building from source

- Java 8 JDK and Apache Ant.
- `ant jar` builds `build/fayte.jar` and `build/lclient-res.jar`.
- For in-game feedback to reach a Discord channel, copy `etc/fayte/feedback.properties.example` to `etc/fayte/feedback.properties` and fill it in (git ignores that file).

## Credits

Built on the Salem client by Fredrik Tolf and the Salem team, and on Ender's and Latikai's custom client work ([Custom-Salem](https://github.com/DonnEssime/Custom-Salem)).

## License

GNU Lesser General Public License v3, like the Salem client it is built on. See `COPYING`.
