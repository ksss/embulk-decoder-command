# embulk-decoder-command

Command decoder plugin for Embulk.

Embulk decoder plugin that exec another process.

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

(If guess supported) you don't have to write `decoder:` section in the configuration file. After writing `in:` section, you can let embulk guess `decoder:` section using this command:

```
$ embulk gem install embulk-decoder-command
$ embulk guess -g exec config.yml -o guessed.yml
```

## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```
