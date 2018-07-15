Embulk::JavaPlugin.register_decoder(
  "command", "org.embulk.decoder.command.CommandDecoderPlugin",
  File.expand_path('../../../../classpath', __FILE__))
