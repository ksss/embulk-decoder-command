Embulk::JavaPlugin.register_decoder(
  "exec", "org.embulk.decoder.exec.ExecDecoderPlugin",
  File.expand_path('../../../../classpath', __FILE__))
