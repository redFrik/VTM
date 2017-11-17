VTMRemoteNetworkNode : VTMElement {
	var <addr;

	*managerClass{ ^VTMNetworkNodeManager; }

	*new{arg name, declaration, manager;
		^super.new(name, declaration, manager).initRemoteNetworkNode;
	}

	initRemoteNetworkNode{
		addr = NetAddr.newFromIPString(this.get(\ipString));
	}

	*parameterDescriptions{
		^super.parameterDescriptions.putAll(VTMOrderedIdentityDictionary[
			\ipString -> (type: \string, optional: false),
			\mac -> (type: \string, optional: false)
		]);
	}

	sendMsg{arg path ...args;
		VTM.sendMsg(addr.hostname, addr.port, path, *args);
	}

	discover{
		VTM.local.discover(addr.hostname);
	}
}
