package net.fabricmc.filament.task;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.filament.task.base.WithFileOutput;

import net.fabricmc.loom.util.ZipUtils;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

@DisableCachingByDefault
public abstract class ExtractMappingsTask extends FilamentTask implements WithFileOutput {
	@InputFiles
	public abstract ConfigurableFileCollection getInputFiles();

	@TaskAction
	public void run() throws IOException {
		File mappingsJar = getInputFiles().getSingleFile();
		Files.write(this.getOutputPath(), ZipUtils.unpack(mappingsJar.toPath(), "mappings/mappings.tiny"), StandardOpenOption.CREATE_NEW, StandardOpenOption.TRUNCATE_EXISTING);
	}
}
