VTMDefinitionLibrary : VTMElement {

	*managerClass{ ^VTMDefinitionLibraryManager; }

	*new{arg name, declaration, manager;
		^super.new(name, declaration, manager).initDefinitionLibrary;
	}

	initDefinitionLibrary{}

	*parameterDescriptions{
		^super.parameterDescriptions.putAll(
			VTMOrderedIdentityDictionary[
				\includedPaths -> (type: \array, itemType: \string),
				\excludedPaths -> (type: \array, itemType: \string)
			]
		);
	}

	*queryDescriptions{
		^super.queryDescriptions.putAll(
		   VTMOrderedIdentityDictionary[
			   \hasDefinition -> (type: \boolean)
		   ]
	   );
	}
}

