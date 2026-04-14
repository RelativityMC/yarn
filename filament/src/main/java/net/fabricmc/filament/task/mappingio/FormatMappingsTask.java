package net.fabricmc.filament.task.mappingio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.filament.task.base.FilamentTask;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;

@DisableCachingByDefault
public abstract class FormatMappingsTask extends FilamentTask {
	@InputDirectory
	@PathSensitive(PathSensitivity.NONE)
	public abstract DirectoryProperty getInput();

	@OutputDirectory
	protected abstract DirectoryProperty getOutput();

	public FormatMappingsTask() {
		getOutput().set(getInput());
	}

	@TaskAction
	void run() throws IOException {
		Path path = getInput().get().getAsFile().toPath();

		MappingWriter writer = MappingWriter.create(path, MappingFormat.ENIGMA_DIR);
		Objects.requireNonNull(writer, "writer");

		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(path, MappingFormat.ENIGMA_DIR, tree);

		for (MappingTree.ClassMapping classMapping : tree.getClasses()) {
			String srcName = classMapping.getSrcName();
			int anonClassIndex;

			try {
				anonClassIndex = Integer.parseInt(srcName.substring(srcName.lastIndexOf('$') + 1));
			} catch (NumberFormatException e) {
				continue;
			}

			for (int dstIdx = 0; dstIdx < tree.getMaxNamespaceId(); dstIdx++) {
				String dstName = classMapping.getDstName(dstIdx);

				if (dstName == null) {
					continue;
				}

				int dstAnonClassIndex;

				try {
					dstAnonClassIndex = Integer.parseInt(dstName.substring(dstName.lastIndexOf('$') + 1));
				} catch (NumberFormatException e) {
					getLogger().warn("Unable to parse dst anon class index for {} -> {}", srcName, dstName);
					continue;
				}

				if (dstAnonClassIndex != anonClassIndex) {
					classMapping.setDstName(dstName.substring(0, dstName.lastIndexOf('$') + 1) + anonClassIndex, dstIdx);
				}
			}
		}

		tree.accept(writer, VisitOrder.createByName());
	}
}
