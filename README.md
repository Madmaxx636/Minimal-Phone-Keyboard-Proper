# Minimal Keyboard Proper

> **This is a modified version of [Minimal SymLayer Keyboard](https://github.com/rickybrent/minimal-symlayer-keyboard) by rickybrent, which is itself a fork of [TitanPocketKeyboard](https://github.com/oin/titanpocketkeyboard) by oin.**
> The keyboard and most of its code are their work and that of their contributors, see [Credits](#credits). What was changed in this version is listed under [What was changed](#what-was-changed-in-this-version). It is released under the same licence, the GNU General Public License v3.

`Minimal SymLayer Keyboard` is an Android input method for the Minimal Phone MP01, though it may be useful for other devices with built in thumbboards. It does not contain a virtual keyboard but adds a SYM layer for physical keys.

The project is a fork of the excellent  [TitanPocketKeyboard by oin](https://github.com/oin/titanpocketkeyboard), which was originally designed for the Unihertz Titan Pocket, and has no relation to Lersi's [Minimal Phone Keyboard](https://github.com/lersi/minimal_phone_keyboard) or the keyboard that shipped with the Minimal Phone.

This fork adds several new features (a keyboard-focused emoji picker, a clipboard history manager, and additional virtual modifier keys) and special handling for the MP01's unique keys and e-ink display.

### A Note for Unihertz Titan Pocket Users

While this project is based on the TitanPocketKeyboard, support for the Unihertz Titan Pocket is **entirely untested** in this fork. Many of the improvements are specific to the Minimal Phone's hardware. However, features like the keyboard-navigable emoji picker and clipboard history might be of interest to Titan Pocket users willing to experiment, and the code has been written with a blind best-effort to support both devices.


## Key Features of this Fork

This fork builds upon the original's solid foundation with several new features and improvements, primarily for the Minimal Phone MP01:

* **Keyboard-Focused Emoji Picker**: A searchable emoji filter accessed by tapping the Emoji/0 key.
* **Clipboard History**: A clipboard manager with search and pinning, accessed via Emoji/0 + V.
    * Records everything copied from the moment the keyboard starts, not only after the clipboard view was first opened.
    * A clear button removes everything that is not pinned.
    * Settings for the history size and for forgetting items after an hour, a day or a week.
    * Items that apps mark as sensitive (such as passwords copied from a password manager) are skipped on Android 13 and later.
* **Emoji Skin Tone**: Pick a skin tone in the settings and the emoji picker shows only that tone instead of listing every variant. Searching for "skin tone" still finds all variants.
* **Toolbar**: An optional strip above the keyboard (Settings > Show toolbar) with buttons for the emoji picker, clipboard history and voice typing, word suggestions, and an icon for every active modifier (Shift, Alt, Ctrl, Meta, Sym, Caps, and the Cyrillic and Korean layers). While the toolbar is showing, Back hides it first, as with any keyboard, and the next Back goes to the app.
* **Word Suggestions, Habits and Auto-correct**: The toolbar always has something to offer, whether or not you are in the middle of a word, and it learns how you write.
    * **While typing a word** it completes it, from the words you use and then from a built-in list of 30,000 common English words (turn that off if you mostly type another language). Once a word is finished it suggests what could follow it.
    * **In an empty field, or after `.` `?` `!` or a new line**, it offers words to start a sentence: the ones you start yours with, then common ones like "I", "The" or "Thanks". **After a space** it predicts the next word from the last two words you typed, then from the last one, then from built-in guesses (see `next_words.txt`), so the slots are never empty.
    * **It learns your habits**: the words you use, which word follows which (including two-word phrases), how you start sentences, how you capitalize ("iPhone", "lol") and the corrections you make. What you do lately counts for more than what you did long ago. All of it is stored only on the device (and left out of backups), never from password fields or fields that ask for no personalized learning, and can be turned off in the settings.
    * **Auto-correct** (`Fix typos automatically`: Off, Low, Medium or High) fixes a misspelled word when you type a space or punctuation after it, or press Enter. The best guess is picked from three sources at once: the spell checker chosen in Android's settings, the built-in common words, and the words you use. Each candidate is weighed like a good keyboard does: how easy the slip is to make (a key next to the right one, two swapped letters, a doubled letter, a vowel for a vowel, the silent *h* of "wich") against how common the word is and whether it fits the word before. Low only fixes a single slip (a wrong, missing, extra or swapped letter), Medium up to two, and High reaches further and also dares a wrong first letter. It leaves alone words you have used before, names in the middle of a sentence, ALL CAPS, slang like "lol", "omg" and "idk", laughter, words stretched on purpose ("sooo"), handles and addresses, and it is off in the Cyrillic and Korean layers. **Backspace right after a fix puts your word back** and it won't be fixed again. A correction you choose (or keep) twice becomes a habit and is applied even where the spell checker has no opinion.
    * **Fix the last word when I press Enter**: a word is normally only fixed once something is typed after it, which never happens to the last word of a message. With this on (the default), the word before the cursor is fixed just before Enter is sent, and when something was changed Enter is sent again straight after the fix, so it goes out corrected.
    * **Space after punctuation** (default on): a full stop, comma, question mark, exclamation mark or semicolon typed straight into the next word gets its space (`hello.world` → `hello. world`, `yes,please` → `yes, please`), and the next sentence gets its capital. Web addresses (`example.com`, `www.…`), file names (`notes.txt`), e-mail addresses, numbers (`3.14`), abbreviations (`e.g`, `U.S.A`) and ellipses are left alone.
    * **Split words that ran together** (default on): a word that is not a word but is made of common ones is split: `lovehahaha` → `love hahaha`, `goodmorning` → `good morning`, `ihave` → `I have`, `iloveyou` → `I love you`. Laughter (`haha`, `hehe`, `lol`, `lmao`) counts as a word. A typo that only looks like two words (`diffrent`) is spelled, not split, and brands like `facebook` and `youtube` are left alone.
    * **Shortcuts and my words** (in the settings): one per line, `omw = on my way` expands when you type a space after it (and keeps your capital: `Omw` → `On my way`), and a word on a line of its own (`Kubernetes`) is never fixed and is suggested as you type it.
    * **Grammar and punctuation** (`Fix grammar and punctuation`: Off, Basic or Full) fixes patterns that are hardly ever right as typed, with the same Backspace-to-undo. **Basic** fixes a lowercase "i", a missing apostrophe ("dont", "shouldve"), "a apple", "could of", "alot" and a doubled "the", puts a capital on the first word of a sentence and on days, months, languages, places and brands ("monday", "london", "iphone" → "iPhone"), fixes a caps lock slip ("hELLO", "THe"), and takes the space away from before a comma, full stop, question mark or exclamation mark. **Full** also chooses between *their*, *there* and *they're* ("there car" → "their car", "their is" → "there is", "their going" → "they're going"), *your*/*you're*, *its*/*it's*, *to*/*too* ("me to." → "me too."), *then*/*than*, *lose*/*loose*, *whose*/*who's*, *affect*/*effect*, *weather*/*whether* and *were*/*we're*/*where* ("were going" at the start of a sentence, "they where"), and fixes "he don't", "they was", "I has", "I have went", "I could've wrote" and "suppose to". This is a set of rules, not a language model: it only looks at the words you have typed before, never at what comes after, so it acts only where a pattern is almost never right ("Does he have" is left alone), and it will miss plenty of mistakes.
    * Corrections come from the spell checker chosen in Android's `Settings` > `System` > `Languages & input` > `Spell checker`. If none is turned on there, the built-in list of common words stands in for it and fixes only the obvious typos, one letter off a common word. `Test the spell checker` in the settings shows what the spell checker answers for a few misspelled words (and one that is two words run together) and what auto-correct would do about them.
    * When a word looks like a typo, the first suggestion is the word as typed in quotes. Tap it to keep your spelling and stop it being flagged.
    * Tap a suggestion to use it, or hold Emoji/0 and press `F`, `G` or `H` for the first, second or third suggestion.
    * **To forget something**, hold a suggestion in the toolbar (a word you don't want, or a prediction that keeps coming up), or use `Manage learned words` in the settings to search everything that was learned and forget words and corrections one at a time. The built-in words can't be forgotten that way.
    * To give it a head start, use `Import text to learn from` in the settings with a text file of your own writing (notes, exported chats, emails) or a book. The list of common words is derived from [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave (CC BY-SA 4.0), see `About` > `Third-Party Licenses`.
* **Voice Typing**: The Mic/Period key, and the toolbar's microphone button, switch to Google voice typing when it is turned on as a keyboard (`Manage keyboards`). Otherwise the system speech dialog (from the Google app) is shown and what you say is typed in afterwards. The settings can prefer any other voice keyboard, or always use the speech dialog.
* **Sym-Layer Preview**: A visual preview of the Sym layer mappings, making it easy to discover and learn.
* **Advanced Key Support for MP01**: Added support for the Minimal Phone's key layout, adding additional functionality over the factory keyboard:
    * **Three-Way Modifier Keys**: The Mic/Period and Emoji/0 keys act as multi-function modifiers:
        * **Mic/Period Key**:
            * **Tap**: Inserts a period (.), or > when Shift is held.
            * **Hold + Key**: Acts as a Ctrl modifier for keyboard shortcuts (e.g., Hold Mic/Period + C for Copy).
            * **Long Press & Release**: Invokes the voice-to-text input (also triggered by Alt + Mic/Period).
        * **Emoji/0 Key**:
            * **Tap**: Opens the emoji picker.
            * **Hold + Key**: Acts as a special function modifier (like the Meta/Win/Command key). For example, Emoji + V opens the clipboard history viewer.
            * **Long Press & Release**: Inserts a 0 (also triggered by pressing while holding Alt).
* **Sym layer updated for the MP01**: The keys on the sym layer have been updated and changed to avoid duplicating any of the alt-keys already available, and to make it possible to enter certain common keys missing from the MP01's alt layer (such as parenthesis.)

The original project's multipress accented characters, ligatures, Cyrillic layer and Korean input are not offered in the settings of this version and are switched off, see [Removed from the settings](#removed-from-the-settings).


## Installation

1. Download the latest APK from the [releases](https://github.com/rickybrent/minimal-symlayer-keyboard/releases) page.
2. Install the APK on your Minimal Phone MP01.
3. Go to `Settings` > `System` > `Languages & input` > `Virtual keyboard` > `Manage keyboards` and enable `Minimal Keyboard Proper`.
4. Select `Minimal Keyboard Proper` as your default input method.




## Core Features from original TitanPocketKeyboard

* **Full Keyboard Layout** with all keys and symbols.
* No space taken on the screen.
* Long press for alternate characters (see list below).
* **Keyboard Navigation**: simulate arrow keys and home/end/page up/page down keys, using the `sym` modifier - especially useful with an e-ink screen.
* Lock modifier keys by double-tapping them, while a single tap will only have effect for the next key press.
* See modifier key state in the status bar.
* Auto-capitalization of the first letter of a sentence.x	
* Two spaces after a period automatically replaced by a period and a space.

## `sym` modifier map and `emoji` shortcuts

Using the `sym` modifier, you can access more keys and symbols.
For instance, you can use `WASD` (and `HJKL` on the Titan Pocket) to navigate in text.

`sym` modifiers are marked in red; `emoji` modifiers are colored blue (some `emoji` modifiers are not yet implemented):

![`sym` modifier map MP01](readme-symbehavior-mp01.png)

The Titan Pocket only has a sym layer, though it has more modifier keys available:
![`sym` modifier map Titan Pocket](readme-symbehavior-titanpocket.png)

The Cut/Copy/Paste actions are only available when no modifier is pressed.

## Keyboard Layout

The layouts below are from the original TitanPocketKeyboard project. **Note that the Sym-layer mapping and some long-press characters have been changed in this fork to better suit the Minimal Phone MP01.** The new Sym-layer preview feature is the best way to explore the current layout.


### Removed from the settings

The original project could type accented characters by pressing a key several times quickly (with templates for French, Spanish, German and more), combine `ae` and `oe` into ligatures, and switch to a Cyrillic layer or to Korean input with a long press of right shift. **This version does not offer them in the settings and they are switched off, whatever an older version saved.** The code for them is still in the project, so they can be brought back, and they are described in the [original project's README](https://github.com/rickybrent/minimal-symlayer-keyboard#readme).

Two more switches were removed and their behaviour fixed: the phone's own alt key map is always replaced by this keyboard's, and the Sym layer's navigation keys always use the default (left hand) layout.

### Additional Characters (after Long Press)


The following table shows the characters that can be accessed with a long press and subsequent multipresses.
The first column is the character printed on the key 

| Key | Long press (Titan) | Long press (MP01) | Following multipresses            |
| --- |--------------------|-------------------|-----------------------------------|
| **`q`** | **`0`**            | `&`               | `°` (degree)                      |
| **`w`** | **`1`**            | `1`               | `&`, `↑`                          |
| **`e`** | **`2`**            | `2`               | `€`, `∃`                          |
| **`r`** | **`3`**            | `3`               | `®`                               |
| **`t`** | **`(`**            | `_`               | `[`, `{`, `<`, `≤`, `†`, `™`      |
| **`y`** | **`)`**            | `-`               | `]`, `}`, `>`, `≥`                |
| **`u`** | **`-`**            | `+`               | `–` (em dash), `–` (en dash), `∪` |
| **`i`** | **`_`**            | `!`               | `\|`                              |
| **`o`** | `ô`                | `#`               | `ó`, `ò`, `ö`, `õ`                |
| **`p`** | **`:`**            | `$`               | `;`, `¶`                          |
| **`a`** | **`@`**            | `@`               | `æ`, `ª`, `←`                     |
| **`s`** | **`4`**            | `4`               | `ß`, `§`, `↓`                     |
| **`d`** | **`5`**            | `5`               | `∂`, `→`, `⇒`                     |
| **`f`** | **`6`**            | `6`               | `^`                               |
| **`g`** | **`*`**            | `=`               | `•`, `·`                          |
| **`h`** | **`#`**            | `:`               | `²`, `♯`                          |
| **`j`** | **`+`**            | `;`               | `=`, `≠`, `≈`, `±`                |
| **`k`** | **`"`**            | `'`               | `%`, `‰`, `‱`                     |
| **`l`** | **`'`**            | `"`               | `` ` ``                           |
| **`z`** | **`!`**            | `7`               | `¡`, `‽`                          |
| **`x`** | **`7`**            | `8`               | `×`, `χ`                          |
| **`c`** | **`8`**            | `9`               | `ç` `©`, `¢`, `⊂`, `⊄`, `⊃`, `⊅`  |
| **`v`** | **`9`**            | `*`               | `∀`, `√`                          |
| **` ` (space bar)** | `	` (tab)| ``                | `⇥`     |
| **`b`** | **`.`**            | `%`               | `…`, `ß`, `∫`, `♭`                |
| **`n`** | **`,`**            | `?`               | `ñ`, `¬`, `∩`                     |
| **`m`** | **`?`**            | `,`               | `$`, `€`, `£`, `¿`                |

# Voice Input

Toggling voice input will switch to a voice-input IME if one is installed and enabled, e.g. Google Voice Input (added with [Gboard](https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin)) or [Whisper Plus](https://github.com/woheller69/whisperIMEplus).

# Customizing and contributing

Feel free to adjust the layout to your needs by modifying the code and building your own version. If you think your changes could be useful to others, please consider contributing them back to this project, the original project, or by making a public fork.


## Credits

Everything here is built on the work of other people, and full credit goes to them:

* **[oin](https://github.com/oin)**, author of [TitanPocketKeyboard](https://github.com/oin/titanpocketkeyboard), the keyboard that all of this is based on. The core features listed under "Core Features from original TitanPocketKeyboard" above, such as the modifier keys, the sym layer, multipress accents and auto-capitalization, come from that project.
* **[rickybrent](https://github.com/rickybrent)**, author of [Minimal SymLayer Keyboard](https://github.com/rickybrent/minimal-symlayer-keyboard), the fork for the Minimal Phone MP01 that this version is a modification of. It added the emoji picker, the clipboard history, the sym layer preview, the special handling of the MP01's keys, and much more.
* **[meldavy](https://github.com/meldavy)** and **[csaba-craft](https://github.com/csaba-craft)**, who contributed to Minimal SymLayer Keyboard, and **Mantas Norvaisa** (the Lithuanian multipress mapping) and **danser** (Cyrillic layout layer support with Alt+key combinations), whose changes are in its history.

Data and artwork used in this version:

* The emoji list is from [Mange/emoji-data](https://github.com/Mange/emoji-data), under the Unicode licence.
* The icons are [Material Icons and Symbols](https://fonts.google.com/icons) by Google, under the Apache License 2.0.
* The built-in list of common words is derived from [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave (`en_50k.txt`, 2018), which was made from the OpenSubtitles 2018 corpus (distributed by [OPUS](https://opus.nlpl.eu/)), under the [Creative Commons Attribution-ShareAlike 4.0 licence](https://creativecommons.org/licenses/by-sa/4.0/). It was cut down and cleaned up, see `app/src/main/res/raw/license_common_words.txt`.

This project has no connection to Lersi's [Minimal Phone Keyboard](https://github.com/lersi/minimal_phone_keyboard).


## What was changed in this version

Modified in September 2026 from Minimal SymLayer Keyboard. The changes are:

* The app is renamed Minimal Keyboard Proper.
* An optional toolbar with quick actions, word suggestions and an icon for every active modifier.
* Word suggestions that are always populated (sentence starters, next words from your last two words, built-in guesses), learning of your habits (words, phrases, sentence starters, capitalization and corrections), auto-correct with three strengths and undo, forgetting learned words one at a time, and a text import to teach it.
* Grammar and punctuation fixes as you type, from a set of rules (their/there/they're, your/you're, its/it's, to/too, then/than, were/we're/where, a/an, capital I, capitals for sentences and names, apostrophes, verb forms after "have" and more), at two strengths, with undo.
* A stronger auto-correct that weighs the spell checker's suggestions, the common words and your own words by how likely each slip is and how common each word is, fixes the last word when Enter is pressed, and leaves slang, laughter and stretched words alone.
* Auto-space: a space after punctuation typed into the next word, and words that ran together split apart (`lovehahaha` → `love hahaha`).
* Text shortcuts and a personal word list (`omw = on my way`).
* Voice typing through Google voice typing, or the system speech dialog when it is not turned on.
* Emoji picker: a skin tone setting, and duplicate emoji removed.
* Clipboard history: records from when the keyboard starts, a clear button, a size limit, expiry, skipping sensitive items, and a fix for removing pinned items.
* The settings for accented characters (multipress, ligatures, Cyrillic layer and Korean input), the override of the alt key map and the right handed navigation cluster were removed. The first group is switched off and the alt key override is always on. The timing settings now say in detail what each one does.
* Tests for the new code.

The original project's own history and authors are on its GitHub page, linked under [Credits](#credits).


## Licence

Like the projects it is based on, this is free software under the [GNU General Public License v3](LICENSE): you may use, study, change and share it under the terms of that licence, and anyone who receives the app is entitled to its source code. The list of common words (`app/src/main/res/raw/common_words.txt`) is under CC BY-SA 4.0, as described above.
