//a singleton class
VTMNetworkNodeManager : VTMDataManager {

	*dataClass{ ^VTMRemoteNetworkNode; }

	name{ ^\networkNodes; }

	*sendToAll{| ...args |
	}
}
