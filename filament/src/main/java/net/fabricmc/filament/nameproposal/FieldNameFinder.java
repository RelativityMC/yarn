/*
 * Copyright (c) 2016, 2021 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.filament.nameproposal;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import net.fabricmc.filament.nameproposal.field.FieldData;
import net.fabricmc.filament.nameproposal.field.nameprovider.FieldNameProvider;

public class FieldNameFinder {
	public Map<MappingEntry, String> findNames(Map<String, Set<String>> allEnumFields, Map<String, List<MethodNode>> classes, FieldNameProvider nameProvider) {
		Objects.requireNonNull(allEnumFields);
		Objects.requireNonNull(classes);
		Objects.requireNonNull(nameProvider);

		Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
		Map<MappingEntry, String> fieldNames = new HashMap<>();
		Set<MappingEntry> attemptedProposals = new HashSet<>();
		Map<String, ConflictChecker<FieldData>> conflictCheckers = new HashMap<>();
		int passes = 0;

		while (true) {
			System.out.println(String.format("Name proposal running (pass %d)", ++passes));

			boolean proposedAnyNames = false;

			for (Iterator<Map.Entry<String, List<MethodNode>>> iterator = classes.entrySet().stream().sorted(Map.Entry.comparingByKey()).iterator(); iterator.hasNext(); ) {
				Map.Entry<String, List<MethodNode>> entry = iterator.next();
				String owner = entry.getKey();
				Set<String> enumFields = allEnumFields.getOrDefault(owner, Collections.emptySet());

				ConflictChecker<FieldData> checker = conflictCheckers.computeIfAbsent(owner, owner1 -> {
					String[] parts = owner1.split("/");
					String shortOwner = parts.length > 0 ? parts[parts.length - 1] : owner1;
					return new ConflictChecker<>(shortOwner + " field");
				});

				for (MethodNode mn : entry.getValue()) {
					proposedAnyNames |= findMethodNames(nameProvider, analyzer, fieldNames, attemptedProposals, owner, enumFields, checker, mn);
				}
			}

			if (!proposedAnyNames) break;
		}

		return fieldNames;
	}

	private boolean findMethodNames(FieldNameProvider nameProvider, Analyzer<SourceValue> analyzer, Map<MappingEntry, String> fieldNames, Set<MappingEntry> attemptedProposals, String owner, Set<String> enumFields, ConflictChecker<FieldData> checker, MethodNode mn) {
		boolean proposedAnyNames = false;

		Frame<SourceValue>[] frames;

		try {
			frames = analyzer.analyze(owner, mn);
		} catch (AnalyzerException e) {
			throw new RuntimeException(e);
		}

		InsnList instrs = mn.instructions;

		for (int i = 1; i < instrs.size(); i++) {
			AbstractInsnNode instr1 = instrs.get(i - 1);
			AbstractInsnNode instr2 = instrs.get(i);

			if (instr2.getOpcode() != Opcodes.PUTSTATIC) continue;
			FieldInsnNode fieldNode = (FieldInsnNode) instr2;

			MappingEntry mappingEntry = new MappingEntry(fieldNode.owner, fieldNode.name, fieldNode.desc);
			if (attemptedProposals.contains(mappingEntry)) continue;

			if (instr1.getOpcode() != Opcodes.INVOKESTATIC && instr1.getOpcode() != Opcodes.INVOKESPECIAL && instr1.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			if (!(instr1 instanceof MethodInsnNode methodNode)) continue;

			var frame = frames[i - 1];
			var args = new Object[frame.getStackSize()];

			for (int j = 0; j < frame.getStackSize(); j++) {
				SourceValue sv = frame.getStack(j);

				for (AbstractInsnNode ci : sv.insns) {
					if (ci instanceof LdcInsnNode node && node.cst instanceof String arg) {
						args[j] = arg;
					} else {
						args[j] = ci;
					}
				}
			}

			boolean isEnum = enumFields.contains(fieldNode.desc + fieldNode.name);
			var field = new FieldData(fieldNode, methodNode, args, true, isEnum, fieldNames);
			var name = nameProvider.getName(field);

			if (name != null) {
				attemptedProposals.add(mappingEntry);

				if (checker.add(name, field)) {
					if (name.equals(((FieldInsnNode) instr2).name)) {
						// No need to map names that are already named what we want to name it.
						continue;
					}

					fieldNames.put(mappingEntry, name);
					proposedAnyNames |= true;
				}
			}
		}

		return proposedAnyNames;
	}
}
