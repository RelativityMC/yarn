package net.fabricmc.filament.common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class LvtStripper implements AutoCloseable {
	public static class Entry {
		public final Path path;
		public final BasicFileAttributes metadata;
		public final byte[] data;

		public Entry(Path path, BasicFileAttributes metadata, byte[] data) {
			this.path = path;
			this.metadata = metadata;
			this.data = data;
		}
	}

	private final FileSystemUtil.Delegate inputFs, outputFs;
	private final Path input;
	private final Map<String, Entry> entries;
	private boolean removeSnowmen = false;
	private boolean offsetSyntheticsParams = false;

	public LvtStripper(File inputServer, File output) throws IOException {
		if (output.exists()) {
			if (!output.delete()) {
				throw new IOException("Could not delete " + output.getName());
			}
		}

		Files.createDirectories(output.toPath().getParent());

		this.input = (inputFs = FileSystemUtil.getJarFileSystem(inputServer, false)).get().getPath("/");
		this.outputFs = FileSystemUtil.getJarFileSystem(output, true);

		this.entries = new HashMap<>();
	}

	public void enableSnowmanRemoval() {
		removeSnowmen = true;
	}

	public void enableSyntheticParamsOffset() {
		offsetSyntheticsParams = true;
	}

	@Override
	public void close() throws IOException {
		inputFs.close();
		outputFs.close();
	}

	private void readToMap(Map<String, Entry> map, Path input) {
		try {
			Files.walkFileTree(input, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
					if (attr.isDirectory()) {
						return FileVisitResult.CONTINUE;
					}

					if (!path.getFileName().toString().endsWith(".class")) {
						if (path.toString().equals("/META-INF/MANIFEST.MF")) {
							map.put("META-INF/MANIFEST.MF", new Entry(path, attr,
									"Manifest-Version: 1.0\nMain-Class: net.minecraft.client.Main\n".getBytes(StandardCharsets.UTF_8)));
						} else {
							if (path.toString().startsWith("/META-INF/")) {
								if (path.toString().endsWith(".SF") || path.toString().endsWith(".RSA")) {
									return FileVisitResult.CONTINUE;
								}
							}

							map.put(path.toString().substring(1), new Entry(path, attr, null));
						}

						return FileVisitResult.CONTINUE;
					}

					byte[] output = Files.readAllBytes(path);
					map.put(path.toString().substring(1), new Entry(path, attr, output));
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void add(Entry entry) throws IOException {
		Path outPath = outputFs.get().getPath(entry.path.toString());

		if (outPath.getParent() != null) {
			Files.createDirectories(outPath.getParent());
		}

		if (entry.data != null) {
			Files.write(outPath, entry.data, StandardOpenOption.CREATE_NEW);
		} else {
			Files.copy(entry.path, outPath);
		}

		Files.getFileAttributeView(outPath, BasicFileAttributeView.class)
				.setTimes(
						entry.metadata.creationTime(),
						entry.metadata.lastAccessTime(),
						entry.metadata.lastModifiedTime()
				);
	}

	public void doTask() throws IOException {
		ExecutorService service = Executors.newFixedThreadPool(2);
		service.submit(() -> readToMap(entries, input));
		service.shutdown();

		try {
			service.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		List<Entry> entries = this.entries.entrySet().parallelStream().map((entry) -> {
			String name = entry.getKey();
			boolean isClass = name.endsWith(".class");
			Entry result = entry.getValue();

			if (result != null) {
				if (isClass) {
					byte[] data = result.data;
					ClassReader reader = new ClassReader(data);
					ClassWriter writer = new ClassWriter(0);
					ClassVisitor visitor = writer;

					visitor = new LvtStripperClassVisitor(Constants.ASM_VERSION, visitor);

					if (visitor != writer) {
						reader.accept(visitor, 0);
						data = writer.toByteArray();
						result = new Entry(result.path, result.metadata, data);
					}
				}

				return result;
			} else {
				return null;
			}
		}).filter(Objects::nonNull).toList();

		for (Entry e : entries) {
			add(e);
		}
	}


	public static class LvtStripperClassVisitor extends ClassVisitor {
		protected LvtStripperClassVisitor(int api, ClassVisitor classVisitor) {
			super(api, classVisitor);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new LvtStripperMethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions));
		}

		public static class LvtStripperMethodVisitor extends MethodVisitor {
			public LvtStripperMethodVisitor(int api, MethodVisitor methodVisitor) {
				super(api, methodVisitor);
			}

			@Override
			public void visitParameter(final String name, final int access) {
				super.visitParameter(null, access);
			}

			@Override
			public void visitLocalVariable(
					final String name,
					final String descriptor,
					final String signature,
					final Label start,
					final Label end,
					final int index) {
				super.visitLocalVariable("$$" + index, descriptor, signature, start, end, index);
			}
		}
	}

}
