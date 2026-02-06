package org.embulk.decoder.command;

import java.io.InputStream;
import java.io.IOException;

import org.embulk.config.ConfigSource;
import org.embulk.config.TaskSource;
import org.embulk.spi.DecoderPlugin;
import org.embulk.spi.Exec;
import org.embulk.spi.FileInput;
import org.embulk.util.config.Config;
import org.embulk.util.config.ConfigMapper;
import org.embulk.util.config.ConfigMapperFactory;
import org.embulk.util.config.Task;
import org.embulk.util.config.TaskMapper;
import org.embulk.util.file.FileInputInputStream;
import org.embulk.util.file.InputStreamFileInput;

public class CommandDecoderPlugin
        implements DecoderPlugin
{
    private static final ConfigMapperFactory CONFIG_MAPPER_FACTORY =
            ConfigMapperFactory.builder().addDefaultModules().build();
    private static final ConfigMapper CONFIG_MAPPER = CONFIG_MAPPER_FACTORY.createConfigMapper();
    private static final TaskMapper TASK_MAPPER = CONFIG_MAPPER_FACTORY.createTaskMapper();

    public interface PluginTask
            extends Task
    {
        @Config("command")
        String getCommand();
    }

    @Override
    public void transaction(ConfigSource config, DecoderPlugin.Control control)
    {
        final PluginTask task = CONFIG_MAPPER.map(config, PluginTask.class);
        control.run(task.toTaskSource());
    }

    @Override
    public FileInput open(TaskSource taskSource, FileInput fileInput)
    {
        final PluginTask task = TASK_MAPPER.map(taskSource, PluginTask.class);

        final FileInputInputStream files = new FileInputInputStream(fileInput);

        return new InputStreamFileInput(
                Exec.getBufferAllocator(),
                new InputStreamFileInput.Provider() {
                    @Override
                    public InputStream openNext() throws IOException
                    {
                        if (!files.nextFile()) {
                            return null;
                        }
                        return new PipedExecInputStream(files, task.getCommand());
                    }

                    @Override
                    public void close() throws IOException
                    {
                        files.close();
                    }
                });
    }
}
