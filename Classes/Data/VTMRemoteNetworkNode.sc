VTMRemoteNetworkNode : VTMElement {
	var <addr;
	var <localNetworks;

	*managerClass{ ^VTMNetworkNodeManager; }

	*new{| name, declaration, manager |
		^super.new(name, declaration, manager).initRemoteNetworkNode;
	}

	initRemoteNetworkNode{
		localNetworks = [];
		if(manager.notNil, {
			localNetworks = localNetworks.add(manager);
		});
		addr = NetAddr.newFromIPString(this.get(\ip));
	}

	//when a computer is available both on WIFI and Cable LAN
	//we add the second one with this method.
	addLocalNetwork{| localNetwork |
		localNetworks = localNetworks.add(localNetwork);
	}

	*parameterDescriptions{
		^super.parameterDescriptions.putAll(VTMOrderedIdentityDictionary[
			\ip -> (type: \string, optional: false),
			\mac -> (type: \string, optional: false)
		]);
	}

	sendMsg{| path ...args |
		VTM.sendMsg(addr.hostname, addr.port, path, *args);
	}

	discover{
		VTM.local.discover(addr.hostname);
	}

	debugString{
		var result = super.debugString;
		result = result ++ "\t'localNetworks':\n";
		if(localNetworks.notNil and: {localNetworks.notEmpty}, {
			result = result ++ "\t\t[\n";
			localNetworks.do({| item, i |
				result = result ++ item.getDiscoveryData.makeTreeString(5);
				result = result ++ "\t\t\t,\n";
			});
			result = result ++ "\t\t]\n";
		});
		^result;
	}

	hasLocalNetwork{| lan |
		var result;
		result = localNetworks.includes(lan);
		^result;
	}
}
