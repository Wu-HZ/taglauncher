# TagLauncher

Tag-based Android launcher with a component-based desktop layout and a ring menu
for fast app filtering.

## Features
- Component-based desktop: search bar, app drawer, app grid
- Tag system with ring menu and pinning
- Hidden apps and per-app tag assignment
- Editable desktop layout with drag and resize
- Import/export of tags (JSON)

## Screenshots
Coming soon.

## Requirements
- Android Studio (Kotlin)
- JDK 11+
- Min SDK 24

## Build
```bash
./gradlew assembleDebug
```

## Install (device/emulator)
```bash
./gradlew installDebug
```

## Notes
- This is a launcher app; set it as the default Home app to use it.
- Uses QUERY_ALL_PACKAGES to list installed apps.

## Contributing
See CONTRIBUTING.md.

## Code of Conduct
See CODE_OF_CONDUCT.md.

## License
MIT
