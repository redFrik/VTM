VTMContextProxy : VTMContext {
	var implementation;

	*new{arg name, parent, description, definition;
		^super.new(name, parent, description, definition).initContextProxy;
	}

	initContextProxy{
		var implClass;
		//determine which implementation to use
		implClass = VTMRemoteContextProxyImplementation;
		//make implementation of correct type //FIXME: only remote proxy implmentation for now.
		implementation = VTMRemoteContextProxyImplementation.new(this, description, definition);
	}

	sendMsg{arg ...msg;
		implementation.sendMsg(*msg);
	}
}

/*'/aaa/modules/toglyd', 'db', -10, 'pan', -1.0;*/
