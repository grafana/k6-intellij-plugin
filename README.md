# k6 Support

<!-- Plugin description -->
[k6](https://k6.io) support

Plugin allows running a scenario directly from the IDE either locally or in a cloud.

Use ``Ctrl+\ `` to run a script locally or ``Ctrl+Shift+\ `` to run in a cloud. 

Cloud token can be defined either in a ``Settings -> Tools -> K6`` or in ``K6_CLOUD_TOKEN`` environment variable.
<!-- Plugin description end -->

## Build plugin from the sources
```bash
./gradlew buildPlugin
````
## Run
Either start IDE bundled with plugin via gradle:
```bash
./gradlew runIdea
```                                             
Or install built plugin manually in the Settings->Plugin section of IDEA

