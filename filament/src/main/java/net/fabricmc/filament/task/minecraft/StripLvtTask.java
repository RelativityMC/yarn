package net.fabricmc.filament.task.minecraft;

import java.io.IOException;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.filament.common.LvtStripper;
import net.fabricmc.filament.task.base.FileOutputTask;

@DisableCachingByDefault
public abstract class StripLvtTask extends FileOutputTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getInputJar();

	@TaskAction
	public void run() throws IOException {
		try (LvtStripper lvtStripper = new LvtStripper(
				getInputJar().getAsFile().get(),
				getOutput().getAsFile().get())) {
			lvtStripper.doTask();
		}
	}
}
