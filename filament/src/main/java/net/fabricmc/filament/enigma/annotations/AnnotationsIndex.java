package net.fabricmc.filament.enigma.annotations;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import cuchaz.enigma.api.view.ProjectView;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public record AnnotationsIndex(Collection<String> annotations, Collection<String> allClasses) {
	public static CompletableFuture<AnnotationsIndex> index(ProjectView project) {
		return CompletableFuture.supplyAsync(() -> {
			List<String> annotations = new ArrayList<>();
			List<String> allClasses = new ArrayList<>(project.getProjectAndLibraryClasses());

			allClasses.parallelStream().forEach(className -> {
				ClassNode bytecode = project.getBytecode(className);

				if (bytecode != null && (bytecode.access & Opcodes.ACC_ANNOTATION) != 0) {
					synchronized (annotations) {
						annotations.add(className);
					}
				}
			});

			indexJdkClasses(Collections.synchronizedList(annotations), Collections.synchronizedList(allClasses));

			return new AnnotationsIndex(annotations, allClasses);
		}).whenComplete((annotationsIndex, throwable) -> {
			if (throwable != null) {
				throwable.printStackTrace();
			}
		});
	}

	private static void indexJdkClasses(List<String> annotations, List<String> allClasses) {
		try (Stream<Path> classes = Files.walk(Paths.get(URI.create("jrt:/"))).parallel()) {
			classes.forEach(path -> {
				if (!Files.isRegularFile(path)) return;
				if (!path.getFileName().toString().endsWith(".class")) return;

				try (InputStream in = Files.newInputStream(path)) {
					ClassReader reader = new ClassReader(in);

					allClasses.add(reader.getClassName());

					if ((reader.getAccess() & Opcodes.ACC_ANNOTATION) != 0) {
						synchronized (annotations) {
							annotations.add(reader.getClassName());
						}
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String getClassName(ZipEntry entry) {
		String name = entry.getName();
		return name.substring("classes/".length(), name.length() - ".class".length());
	}
}
