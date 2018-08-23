//a Singleton class that communicates with the network and manages Applications
VTMLocalNetworkNode {
	classvar <singleton;
	classvar <discoveryBroadcastPort = 57500;
	var <hostname;
	var <localNetworks;
	var discoveryResponder;
	var discoveryReplyResponder;
	var remoteActivateResponder;
	var shutdownResponder;

	var <library;

	//global data managers for unnamed contexts
	var <applicationManager;
	var <hardwareSetup;
	var <moduleHost;
	var <sceneOwner;
	var <scoreManager;
	var <networkNodeManager;
	var <cueManager;

	var <active = false;

	*initClass{
		Class.initClassTree(VTMData);
		Class.initClassTree(VTMNetworkNodeManager);
		Class.initClassTree(VTMDefinitionLibrary);
		singleton = super.new.initLocalNetworkNode;
	}

	*new{
		^singleton;
	}

	initLocalNetworkNode{
		applicationManager = VTMApplicationManager.new(nil, this);
		networkNodeManager = VTMNetworkNodeManager.new(nil, this);
		hardwareSetup = VTMHardwareSetup.new(nil, this);
		moduleHost = VTMModuleHost.new(nil, this);
		sceneOwner = VTMSceneOwner.new(nil, this);
		scoreManager = VTMScoreManager.new(nil, this);
		cueManager = VTMCueManager.new(nil, this);

		hostname = Pipe("hostname", "r").getLine();
		if(".local$".matchRegexp(hostname), {
			hostname = hostname.drop(-6);
		});
		hostname = hostname.asSymbol;
		this.findLocalNetworks;
		NetAddr.broadcastFlag = true;
		StartUp.add({
			//Make remote activate responder
			remoteActivateResponder = OSCFunc({arg msg, time, addr, port;
				var hostnames = VTMJSON.parse(msg[1]);
				if(hostnames.detect({arg item;
					item == this.name;
				}).notNil, {
					"Remote VTM activation from: %".format(addr).vtmdebug(2, thisMethod);
					this.activate(doDiscovery: true);
				})
			}, '/activate', recvPort: this.class.discoveryBroadcastPort);
		});

	}

	activate{arg doDiscovery = false, remoteNetworkNodesToActivate;

		if(discoveryResponder.isNil, {
			discoveryResponder = OSCFunc({arg msg, time, resp, addr;
				var jsonData = VTMJSON.parse(msg[1]).changeScalarValuesToDataTypes;
				var senderHostname, senderAddr, registered = false;
				var localNetwork;
				senderHostname = jsonData['hostname'].asSymbol;
				senderAddr = NetAddr.newFromIPString(jsonData['ipString'].asString);

				//find which network the node is sending on
				localNetwork = localNetworks.detect({arg net;
					net.isIPPartOfSubnet(senderAddr.ip);
				});

				if(localNetwork.isNil, {
					"Discovery was sent from a network where this network node is not connected:" ++
					"\thostname: %".format(senderHostname) ++
					"\taddress: %".format(senderAddr).vtmdebug(1, thisMethod);
				}, {
					//Check if it the local computer that sent it.
					if(senderAddr.isLocal.not, {
						//a remote network node sent discovery
						var isAlreadyRegistered;
						isAlreadyRegistered = networkNodeManager.hasItemNamed(senderHostname);
						if(isAlreadyRegistered.not, {
							var newNetworkNode;
							"Registering new network node:" ++
							"\tname: '%'".format(senderHostname) ++
							"\taddr: '%'".format(senderAddr).vtmdebug(1, thisMethod);
							newNetworkNode = VTMRemoteNetworkNode(
								senderHostname,
								(
									ipString: jsonData['ipString'].asString,
									mac: jsonData['mac'].asString
								),
								networkNodeManager,
								localNetwork
							);
						}, {
							var networkNode = networkNodeManager[senderHostname];
							//Check if it sent on a different local network
							if(networkNode.hasLocalNetwork(localNetwork).not, {
								//add the new local network to the remote network node
								networkNode.addLocalNetwork(localNetwork);
							});
						});
						this.sendMsg(
							senderAddr.hostname,
							this.class.discoveryBroadcastPort,
							'/discovery/reply',
							localNetwork.getDiscoveryData
						);
					});
				});
			}, '/discovery', recvPort: this.class.discoveryBroadcastPort);
		});

		if(discoveryReplyResponder.isNil, {
			discoveryReplyResponder = OSCFunc({arg msg, time, addr, port;
				var jsonData = VTMJSON.parse(msg[1]).changeScalarValuesToDataTypes;
				var senderHostname, senderAddr, registered = false;
				var localNetwork;
				senderHostname = jsonData['hostname'].asSymbol;
				senderAddr = NetAddr.newFromIPString(jsonData['ipString'].asString);
				//find which network the node is sending on

				localNetwork = localNetworks.detect({arg net;
					net.isIPPartOfSubnet(senderAddr.ip);
				});

				//Check if it the local computer that sent it.
				if(senderAddr.isLocal.not, {
					//a remote network node sent discovery
					var isAlreadyRegistered;
					isAlreadyRegistered = networkNodeManager.hasItemNamed(senderHostname);
					if(isAlreadyRegistered.not, {
						var newNetworkNode;
						"Registering new network node:" ++
						"\tname: '%'".format(senderHostname) ++
						"\taddr: '%'".format(senderAddr).vtmdebug(2, thisMethod);
						newNetworkNode = VTMRemoteNetworkNode(
							senderHostname,
							(
								ipString: jsonData['ipString'].asString,
								mac: jsonData['mac'].asString
							),
							networkNodeManager,
							localNetwork
						);
						newNetworkNode.discover;
					}, {
						var networkNode = networkNodeManager[senderHostname];
						//Check if it sent on a different local network
						if(networkNode.hasLocalNetwork(localNetwork).not, {
							//add the new local network to the remote network node
							networkNode.addLocalNetwork(localNetwork);
						});
					});
				});
			}, '/discovery/reply', recvPort: this.class.discoveryBroadcastPort);
		});

		if(shutdownResponder.isNil, {
			shutdownResponder = OSCFunc({arg msg, time, addr, port;
				var senderHostname;
				senderHostname = VTMJSON.parseYAMLValue(msg[1].asString);
				//Check if it the local computer that sent it.
				if(addr.isLocal.not, {
					//a remote network node notifued shutdown
					if(networkNodeManager.hasItemNamed(senderHostname), {
						var networkNode = networkNodeManager[senderHostname];
						networkNode.free;
						"Remote network node: '%' sent '/shutdown'".format(senderHostname).vtmdebug(1, thisMethod);
					});
				});
			}, '/shutdown', recvPort: this.class.discoveryBroadcastPort);
		});

		//Notify shutdown to other nodes
		ShutDown.add({
			"Shutting down VTM".vtmdebug(1, thisMethod);
			[
				shutdownResponder,
				discoveryReplyResponder,
				discoveryResponder,
				remoteActivateResponder
			].do({arg resp; resp.clear; resp.free;});

			networkNodeManager.items.do({arg remoteNetworkNode;
				this.sendMsg(
					remoteNetworkNode.addr.hostname.asString,
					this.class.discoveryBroadcastPort,
					'/shutdown',
					hostname
				);
			});
		});

		active = true;
		if(remoteNetworkNodesToActivate.notNil, {
			this.activateRemoteNetworkNodes(remoteNetworkNodesToActivate);
		});

		if(doDiscovery) { this.discover(); }

	}

	activateRemoteNetworkNodes{arg remoteHostnames;
		this.broadcastMsg('/activate', remoteHostnames);
	}

	deactivate{
		discoveryResponder !? {discoveryResponder.free;};
		discoveryReplyResponder !? {discoveryReplyResponder.free;};
		shutdownResponder !? {shutdownResponder.free;};
		remoteActivateResponder !? {remoteActivateResponder.free;};
		active = false;
	}

	applications{
		^applicationManager.applications;
	}

	modules{
		^moduleHost.items;
	}

	findLocalNetworks{
		var lines;
		var parseOSXIfconfig = {arg lns;
			var result, entries;

			lns.collect({arg line;
				if(line.first != Char.tab, {
					entries = entries.add([line]);
				}, {
					entries[entries.size - 1] = entries[entries.size - 1].add(line);
				});
			});

			//remove the entries that don't have any extra information
			entries = entries.reject({arg item; item.size == 1});

			//remove the LOOPBACK entry(ies)
			entries = entries.reject({arg item;
				"[,<]?LOOPBACK[,>]?".matchRegexp(item.first);
			});

			//get only the active entries
			entries = entries.reject({arg item;
				item.any({arg jtem;
					"status: inactive".matchRegexp(jtem);
				})
			});
			//get only the lines with IPV4 addresses and
			entries = entries.collect({arg item;
				var inetLine, hwLine;
				inetLine = item.detect({arg jtem;
					"\\<inet\\>".matchRegexp(jtem);
				});
				if(inetLine.notNil, {
					hwLine = item.detect({arg jtem;
						"\\<ether\\>".matchRegexp(jtem);
					})
				});
				[inetLine, hwLine];
			});
			//remove all that are nil
			entries = entries.reject({arg jtem; jtem.first.isNil; });
			//separate the addresses
			entries.collect({arg item;
				var ip, bcast, mac, netmask;
				var inetLine, hwLine;
				#inetLine, hwLine = item;

				ip = inetLine.copy.split(Char.space)[1];
				bcast = inetLine.findRegexp("broadcast (.+)");
				bcast = bcast !? {bcast[1][1];};
				mac = hwLine.findRegexp("ether (.+)");
				mac = mac !? {mac[1][1]};
				netmask = inetLine.findRegexp("netmask (.+?)\\s");
				netmask = netmask !? {netmask[1][1].interpret.asIPString;};
				(
					ip: ip.stripWhiteSpace,
					broadcast: bcast.stripWhiteSpace,
					mac: mac.stripWhiteSpace,
					hostname: this.hostname,
					netmask: netmask
				)
			}).collect({arg item;
				result = result.add(VTMLocalNetwork.performWithEnvir(\new, item));
			});
			result;
		};
		var parseLinuxIfconfig = {arg lns;
			var result, entries;

			lns.collect({arg line;
				if(line.first != Char.space, {
					entries = entries.add([line]);
				}, {
					entries[entries.size - 1] = entries[entries.size - 1].add(line);
				});
			});

			//remove the entries that don't have any extra information
			entries = entries.reject({arg item; item.size == 1});

			//remove the LOOPBACK entry(ies)
			entries = entries.reject({arg item;
				"[,<]?LOOPBACK[,>]?".matchRegexp(item.first);
			});

			//get only the active entries
			entries = entries.reject({arg item;
				item.any({arg jtem;
					"status: inactive".matchRegexp(jtem);
				})
			});

			//get only the lines with IPV4 addresses and MAC address
			entries = entries.collect({arg item;
				var inetLine, hwLine;
				inetLine = item.detect({arg jtem;
					"\\<inet\\>".matchRegexp(jtem);
				});
				if(inetLine.notNil, {
					hwLine = item.detect({arg jtem;
						"\\<ether\\>".matchRegexp(jtem);
					})
				});
				[inetLine, hwLine];
			});

			//remove all that are nil
			entries = entries.reject({arg jtem; jtem.first.isNil; });

			//separate the addresses
			entries.collect({arg item;
				var ip, bcast, mac, netmask;
				var inetLine, hwLine;
				#inetLine, hwLine = item;

				ip = inetLine.findRegexp("inet ([^\\s]+)");
				ip = ip !? {ip[1][1];};
				bcast = inetLine.findRegexp("broadcast ([^\\s]+)");
				bcast = bcast !? {bcast[1][1];};
				mac = hwLine.findRegexp("ether ([^\\s]+)");
				mac = mac !? {mac[1][1]};
				netmask = inetLine.findRegexp("netmask ([^\\s]+)");
				netmask = netmask !? {netmask[1][1];};
				(
					ip: ip.stripWhiteSpace,
					broadcast: bcast.stripWhiteSpace,
					mac: mac.stripWhiteSpace,
					hostname: this.hostname,
					netmask: netmask
				);
			}).collect({arg item;
				result = result.add(VTMLocalNetwork.performWithEnvir(\new, item));
				nil;
			});
			result;
		};

		//delete previous local networks
		localNetworks = [];
		lines = "ifconfig".unixCmdGetStdOutLines;
		//clump into separate network interface entries

		Platform.case(
			\osx, {
				localNetworks = parseOSXIfconfig.value(lines);
			},
			\linux, {
				localNetworks = parseLinuxIfconfig.value(lines);

			},
			\windows, {
				"No find local network method for Windows yet!".warn;
			}
		);
	}

	name{
		^this.hostname;
	}

	fullPath{
		^'/';
	}

	discover {arg targetHostname;
		//Broadcast discover to all network connections
		if(localNetworks.isNil, { ^this; });
		localNetworks.do({arg network;
			var data, targetAddr;

			data = network.getDiscoveryData;

			// if the method argument is nil, the message is broadcasted

			if(targetHostname.isNil, {
				targetAddr = NetAddr(
					network.broadcast,
					this.class.discoveryBroadcastPort
				);
			}, {
				targetAddr = NetAddr(targetHostname, this.class.discoveryBroadcastPort);
			});

			this.sendMsg(
				targetAddr.hostname, targetAddr.port, '/discovery', data
			);
		});
	}

	*leadingSeparator { ^$/; }

	sendMsg{arg targetHostname, port, path ...data;
		//sending eeeeverything as typed YAML for now.
		NetAddr(targetHostname, port).sendMsg(path, VTMJSON.stringify(data.unbubble));
	}

	broadcastMsg{arg path ...data;
		if(localNetworks.notNil, {
			localNetworks.do({arg item;
				item.broadcastAddr.sendMsg(path, VTMJSON.stringify(data.unbubble));
			})
		});
	}

	findManagerForContextClass{arg class;
		var managerObj;
		case
		{class.isKindOf(VTMModule.class) } {managerObj =  moduleHost; }
		{class.isKindOf(VTMHardwareDevice.class) } {managerObj =  hardwareSetup; }
		{class.isKindOf(VTMScene.class) } {managerObj =  sceneOwner; }
		{class.isKindOf(VTMScore.class) } {managerObj =  scoreManager; }
		{class.isKindOf(VTMApplication.class) } {managerObj =  applicationManager; }
		{class.isKindOf(VTMCue.class) } {managerObj =  cueManager; }
		{class.isKindOf(VTMRemoteNetworkNode.class) } {managerObj =  networkNodeManager; };
		^managerObj;
	}

	makeView{arg parent, bounds, viewDef, settings;
		var viewClass = 'VTMLocalNetworkNodeView'.asClass;
		//override class if defined in settings.
		^viewClass.new(parent, bounds, viewDef, settings, this);
	}
}
