# Changelog

## 6.2.4

- Rhine display settings: a new Material page with column colour strength, background blur, background saturation, frost whiteness and grain, previewed live
- Tasks widget: no longer switches text size on its own; it draws at the chosen size from the first frame
- Tasks widget: tasks with sections show their step count again, and their rows no longer have a blank gap

## 6.2.3

- Rhine: blocks are frosted glass with crisp outlines and edges; only what you see through the glass is blurred, instead of blurring whole blocks
- Rhine: each block's title now sits on its long face with the picture, and blocks are thinner, like discs; the top face no longer carries text and the parent name is no longer repeated on every block
- Rhine: every block in a column takes the colour of its first block (the category, or the task on the section layer), gradients included, spread evenly over the block; this replaces the rainbow sheen
- Rhine display settings: one title size instead of top and side sizes; block depth now ranges 4–16 dp (default 8) and the gap along a column defaults to 24 dp
- Tasks widget: gradient title colours now show as a gradient instead of only the first colour

## 6.2.2

- Rhine: every block with a picture shows it, not only the selected one; blocks away from the selection are blurred more lightly than before
- Rhine: glass blocks catch the light like a disc, with a soft rainbow sheen that slides as you move, a window glint and bright edges
- Rhine editor: the lifted block has the same glass look, and the block array stays behind it as a softly blurred background

## 6.2.1

- Text colours now show exactly as picked; the automatic darkening that changed chosen colours is gone
- The colour picker takes RGB and hex values, and can make a left-to-right gradient of up to 5 colours
- Sections have their own title colour, solid or gradient; black by default
- Copy and paste colours between categories, tasks and sections from the colour picker
- Rhine: blocks are now clear glass, so the blocks and pictures behind show through; only the selected block stays sharp and everything else is blurred, more so further away

## 6.1.3

- Rhine: blocks are now frosted warm glass with a soft depth blur towards the distance; the focused task shows its whole image like the category block, seen through the glass blocks in front, replacing the thin strips from 6.1.2

## 6.1.2

- Rhine: tasks with an image now show it in the category view as a strip along the side of their block, in place of the repeated category name; the category's own image is unchanged

## 6.1.1

- Selected categories show as a raised bright chip with bold text instead of a black outline; finished tasks settle into a darker card with a struck-through title and an olive check mark
- Rhine: picking an image no longer closes the editor when the gallery opens full screen or in portrait; the editor stays open so you can still save or cancel
- Rhine: fixed a draft name from a previous editor appearing on another category or task after the screen was recreated

## 6.1.0

- Restyled the Task page, Settings, dialogs, sheets and the task widget with a warm liquid glass material, with a Reduce transparency switch in Look and Feel
- Rhine: replaced the right-side focus panel with a bottom glass toolbar; search and position panels are glass overlays
- Rhine: opening a Category now lifts the block and turns the camera before fading into its Tasks
- Rhine: editing opens a split view with a live 3D preview of the block beside a glass editor, with lift, turn and return animations
- Optional gallery images for Categories and Tasks, picked in the Rhine editor or the Task page and shown on the block's side face; images stay on the device and are not part of backups
- Added an image fit setting (top-left crop, centre crop or whole image) to the Rhine display settings

## 6.0.8

- Renamed the app to Grhinet; all other functionality and styling are unchanged

## 6.0.7

- Renamed the app to Rrit_Rhine
- Replaced dark selected category fills with a light background and black outline, including Today
- Added a darker outline to completed task cards
- Released the Category / Task / Section workspace and the Rhine view with search, ordering and display settings
- Uses a separate Rhine database and the grit_rhine formatVersion=1 backup format

## 6.0.6

- Fixed task updates replacing habit widgets by refreshing widgets through their registered providers
- Refresh habit widgets after edits, completions, undo, date changes and app upgrades
- Replaced task and habit steps with sections featuring editable names and multiline content
- Added sequential default section names and preserved completion state, ordering and reordering
- Import legacy steps as section content named section 1, section 2, and so on

## 6.0.4
- Updated Translations
- Increased habit title and description length limits

## 6.0.3
- Updated Translations
- Calendar UI Improvements
- Improved Floating Action Button Visibility #299 @therealVitia

## 6.0.2
- Updated Translations
- Miscellaneous Bug Fixes and Improvements

## 6.0.0
- Added new Consistency Stat in Habit Analytics
- Redesigned Weekly Comparison Stat, Displays weekly comparison up to 1 year
- Miscellaneous UI improvements #291
- Monthly Progress Stat can be expanded to a calendar view, up to January 2024
- Fixed Habits being created with no selected days
- Updated Translations
- Thanks to everyone for 1000+ stars on GitHub 🙏

## 5.9.25
- Fix Hebrew Translations not applied by Language Picker

## 5.9.24
- Updated Translations
- Added ability to choose export locations for backups
- Miscellaneous UI improvements

## 5.9.23
- Updated Translations
- (Android 13+) Added ability to change app language from Settings

## 5.9.22
- Updated Translations
- Fixed Unable to change Task Categories

## 5.9.21
- Updated Translations

## 5.9.2
- Miscellaneous UI fixes
- Updated Translations
- Added Keyboard Autofocus to new Habit (#280)

## 5.9.1
- Miscellaneous fixes and updates
- Updated Translations

## 5.9.0
- Fixed Floating action button position on large displays
- Fixed Icons for RTL languages (#272)
- Added Preview images for widgets in widget picker (#273)
- Added Expressive Motion scheme to animations
- Updated Translations

## 5.8.4
- Changed Navigation Animation
- Misc UI fixes

## 5.8.3
- Improved Navigation Experience
- Updated Translations

## 5.8.2
- Updated Translations
                                                            
## 5.8.1
- Fixed tasks notification action not working

## 5.8.0
- Revamped UI to be more expressive
- Added ability to view tasks completed in each day in Habit Heat Map

## 5.7.4
- Fixed Habit streak widget text clipping on large display size #259

## 5.7.3
- Fixed widgets randomly changing into other widgets #256
- Fixed current streak calculation

## 5.7.2
- Habits overview widget now only displays set habits for the day #253

## 5.7.1
- Isolated flex typography to uses, Fixing overflow wrapping #250

## 5.7.0
- Added flex fonts and typography
- Redesigned Weekly comparison and week day breakdown charts
- Extracted week day selection from reminders in habit create/edit sheet
- Removed Weekly graph from overall analytics (looked messy)
- Made keyboard animations smoother on editsheets #248

## 5.6.2
- Fixed past reminders not working #241

## 5.6.1
- Fixed time picker UI flicker #237
- Notifications are now dismissed if marked as done within app #238

## 5.6.0
- Fixed Tasks widget corners, Added Refresh button
- Fixed Habits Overview Widget proportions, Added Refresh button
- Revamped Habit Streaks widget
- Added Habit Week Chart Widget
- Added dynamic Widget previews for launchers
- Changed Notification Icon to previous icon
- Fixed settings UI on landscape mode

## 5.5.53
- Fixed Notifications not working #229

## 5.5.52
- Fixed time picker mismatch #193
- Refreshed habit cards design
- Added google sans and system default font options
- Added toggle for different timepicker types #224
- Fixed Text wrap #197
- Fixed incorrect backup dates #216
- Dependency updates and misc fixes

## 5.5.51
- Fixed widgets stuck on loading
- Added weblate info on settings
- Dependency updates

## 5.5.5
Due to life and college stuff, wasn't able to ship properly for a long time. This update is mostly 
a maintenance and getting familiar with the codebase again. expect more releases soon

- Redid all icons due to technical reasons
- migration and refactors to new libs
- tasks and categories add sheet now open keyboard on launch (#206) by @madhan-selvaraj
- miscellaneous fixes and improvements

## 5.5.4
- Removed unnecessary permissions #184

## 5.5.3
- Fixed weird widget behaviour #170 #192
- Edited widget previews

## 5.5.2
- Fixed Habit Notifications not appearing.

## 5.5.1
- Replaced habit heatmap month text to abbreviated month
- updated metadata

## 5.5.0
- Made UI adaptive to larger screen sizes
- Added grit server to manage tasks and habits on desktop in sync with app
- Made dates clickable on Habits list (#173)
- Optimised task reordering logic (#182)
- Added delete button on task edit sheet (#181)
- Replaced "Edit" with "Save" on task edit sheet (#180)
- Removed about libraries (#168)

## 5.1.0
- Added new all tasks widget
- Tweaked Habit Widget

## 5.0.2
- Updated deps to fix misc bugs
- Testing automatic release
