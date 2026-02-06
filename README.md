# embulk-decoder-command

Command decoder plugin for Embulk.

Embulk decoder plugin that executes another process.

This plugin supports Embulk `0.11` only.

This plugin implement like this command image.

```
$ embulk-input-plugin | lzop -dc | embulk-filter-plugin | ...
                        ^^^^^^^^
                         (here)
```

You can decode input with any process.

## Overview

* **Plugin type**: decoder
* **Guess supported**: no
* **Embulk compatibility**: 0.11

## Configuration

- **command**: exec command (string, required)

## Example

```yaml
in:
  type: any output input plugin type
  decoders:
    - type: command
      command: lzop -dc
```

## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```
