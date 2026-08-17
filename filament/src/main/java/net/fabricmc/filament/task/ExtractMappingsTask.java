package net.fabricmc.filament.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileOutput;
import net.fabricmc.loom.util.ZipUtils;

@DisableCachingByDefault
public abstract class ExtractMappingsTask extends FilamentTask implements WithFileOutput {
	@InputFiles
	@PathSensitive(PathSensitivity.NONE)
	public abstract ConfigurableFileCollection getInputFiles();

	@TaskAction
	public void run() throws IOException {
		File mappingsJar = getInputFiles().getSingleFile();
		Files.write(this.getOutputPath(), ZipUtils.unpack(mappingsJar.toPath(), "mappings/mappings.tiny"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}
}
