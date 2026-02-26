# Datadog Sample Apps

## Getting Started

These sample apps are configured based on configuration JSON files which need to be added in `config` folder in your root directory.
For each flavor, you must provide a config file named `[flavorName].json`. By default, flavors should match one of the existing sites in the `FlashcatSite` enum (for example: `cn`, `staging`).

Example of a minimal sample app configuration file:

```json
{
    "token": "YOUR APP TOKEN",
    "rumApplicationId": "YOUR RUM APPLICATION ID"
}
```

## SDK Flavors

The Kotlin sample app now supports an `sdk` flavor dimension to allow testing with different SDK implementations:

- `full`: Uses the real, functional Datadog SDK modules. (Default)
- `noop`: Uses the pure "No-Op" modules which have empty implementations and minimal footprint.

You can switch between them using Build Variants in Android Studio or via CLI:

```bash
# Build with full SDK
./gradlew :sample:kotlin:assembleCnFullDebug

# Build with No-Op SDK
./gradlew :sample:kotlin:assembleCnNoopDebug
```

## Advanced configuration

### Remote API

To allow the download of logs (to test the `Data List` screen), add the following attributes. You can find them in the `Organization Settings` page in Datadog.

```json
{
    "apiKey": "YOUR API ID",
    "applicationKey": "YOUR APPLICATION KEY"
}
```

### Staging

If you need to target a site that is not part of the `FlashcatSite` enum, configure custom endpoints using the following attributes:

```json
{
    "logsEndpoint": "http://api.example.com/logs",
    "tracesEndpoint": "http://api.example.com/spans",
    "rumEndpoint": "http://api.example.com/rum"
}
```
