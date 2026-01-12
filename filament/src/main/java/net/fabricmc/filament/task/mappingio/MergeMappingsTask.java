package net.fabricmc.filament.task.mappingio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.loom.util.Pair;

public abstract class MergeMappingsTask extends MappingOutputTask {
	@InputFiles
	public abstract ConfigurableFileCollection getMappingInputs();

	@Override
	void run(MappingWriter writer) throws IOException {
		var mappingTree = new MemoryMappingTree();

		for (File file : getMappingInputs().getFiles()) {
			var nsSwitch = new MappingSourceNsSwitch(mappingTree, "intermediary");
			MappingReader.read(file.toPath(), nsSwitch);
		}

		fixInnerClasses(mappingTree);

		MemoryMappingTree officialTree = new MemoryMappingTree();
		var nsCompleter = new MappingNsCompleter(officialTree, Map.of("named", "intermediary"), true);
		var dstReorder = new MappingDstNsReorder(nsCompleter, List.of("intermediary", "named"));
		var sourceNsSwitch = new MappingSourceNsSwitch(dstReorder, "official");
		mappingTree.accept(sourceNsSwitch);

		inheritMappedNamesOfEnclosingClasses(officialTree);
		cleanupMappingLeakageToOfficial(officialTree);
		officialTree.accept(writer);
	}

	private void fixInnerClasses(MemoryMappingTree mappingTree) {
		int named = mappingTree.getNamespaceId("named");

		for (MappingTree.ClassMapping entry : mappingTree.getClasses()) {
			String name = entry.getName(named);

			if (name != null) {
				continue;
			}

			entry.setDstName(matchEnclosingClass(entry.getSrcName(), mappingTree), named);
		}
	}

	/*
	 * Takes something like net/minecraft/class_123$class_124 that doesn't have a mapping, tries to find net/minecraft/class_123
	 * , say the mapping of net/minecraft/class_123 is path/to/someclass and then returns a class of the form
	 * path/to/someclass$class124
	 */
	private String matchEnclosingClass(String sharedName, MemoryMappingTree mappingTree) {
		final int named = mappingTree.getNamespaceId("named");
		final String[] path = sharedName.split(Pattern.quote("$"));

		for (int i = path.length - 2; i >= 0; i--) {
			final String currentPath = String.join("$", Arrays.copyOfRange(path, 0, i + 1));
			final MappingTree.ClassMapping match = mappingTree.getClass(currentPath);

			if (match != null && match.getName(named) != null) {
				return match.getName(named) + "$" + String.join("$", Arrays.copyOfRange(path, i + 1, path.length));
			}
		}

		return sharedName;
	}

	/**
	 * Searches the mapping tree for inner classes with no mapped name, whose enclosing classes have mapped names.
	 * Currently, Yarn does not export mappings for these inner classes.
	 */
	private static void inheritMappedNamesOfEnclosingClasses(MemoryMappingTree tree) {
		assert tree.getNamespaceId("intermediary") > MappingTree.SRC_NAMESPACE_ID;

		// Create an index by intermediary names for faster lookups during the propagation
		tree.setIndexByDstNames(true);

		tree.propagateOuterClassNames("intermediary", List.of("named"), false);
	}

	/**
	 * When merging mappings, intermediary names for methods can ended up leaking into the official namespace.
	 * This is because mapping-io does not have class inheritance information when doing so.
	 * Workaround this problem by deleting invalid mapping entries.
	 */
	private static void cleanupMappingLeakageToOfficial(MemoryMappingTree tree) {
		int intermediaryId = tree.getNamespaceId("intermediary");
		int officialId = tree.getNamespaceId("official");

		List<Pair<String, String>> entriesToRemove = new ArrayList<>();

		for (MappingTree.ClassMapping classMapping : tree.getClasses()) {
			for (MappingTree.MethodMapping methodMapping : classMapping.getMethods()) {
				String intermediary = methodMapping.getName(intermediaryId);
				String official = methodMapping.getName(officialId);

				if (intermediary != null && official != null && intermediary.startsWith("method_") && intermediary.equals(official)) {
					entriesToRemove.add(new Pair<>(methodMapping.getSrcName(), methodMapping.getSrcDesc()));
				}
			}

			for (Pair<String, String> entry : entriesToRemove) {
				classMapping.removeMethod(entry.left(), entry.right());
			}
		}
	}
}
