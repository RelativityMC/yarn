package net.fabricmc.filament.task;

import java.io.File;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.filament.mappingpoet.MappingPoet;

@DisableCachingByDefault
public abstract class MappingPoetTask extends DefaultTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getMappings();
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getMinecraftJar();
	@InputFiles
	@PathSensitive(PathSensitivity.NONE)
	public abstract ConfigurableFileCollection getLibraries();
	@OutputDirectory
	public abstract DirectoryProperty getOutput();

	@TaskAction
	public void run() {
		MappingPoet.generate(
				getMappings().get().getAsFile().toPath(),
				getMinecraftJar().get().getAsFile().toPath(),
				getOutput().get().getAsFile().toPath(),
				getLibraries().getFiles().stream().map(File::toPath).toList()
		);
	}
}
