VTMAbstractNetworkNode : VTMElement {
	classvar <applications;

	*new{arg name, attributes, manager;
		^super.new(name, attributes, manager).initAbstractNetworkNode;
	}

	initAbstractNetworkNode{}

	*registerApplication{

	}

	*unregisterApplication{

	}

	*checkApplications{
	}
}
