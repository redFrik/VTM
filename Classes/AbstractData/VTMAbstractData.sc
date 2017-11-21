VTMAbstractData {
	var <name;
	var <manager;
	var path;
	var parameters;
	var oscInterface;
	var declaration;

	classvar viewClassSymbol = \VTMAbstractDataView;

	*managerClass{
		^this.subclassResponsibility(thisMethod);
	}

	//name is mandatory, must be defined in arg or declaration
	*new{arg name, declaration, manager;
		if(name.isNil, {
			if(declaration.notNil and: {declaration.isKindOf(Dictionary)}, {
				if(declaration.includesKey(\name), {
					name = declaration[\name];
				});
			});
		});
		if(name.isNil, {
			Error(
				"% - 'name' not defined".format(this)
			).throw;
		});
		^super.new.initAbstractData(name, declaration, manager);
	}

	initAbstractData{arg name_, declaration_, manager_;
		name = name_;
		manager = manager_;
		declaration = VTMDeclaration.newFrom(declaration_ ? []);
		declaration.put(\name, name);
		path = declaration[\path];
		this.prInitParameters;
		if(manager.notNil, {
			manager.addItem(this);
			this.addDependant(manager);
		});
	}

	//get the parameter values from the declaration
	//Check for missing mandatory parameter values
	prInitParameters{
		var tempAttr;
		var paramDecl = VTMOrderedIdentityDictionary.new;

		this.class.parameterDescriptions.keysValuesDo({arg key, val;
			var tempVal;
			//check if parameter is defined in parameter values
			if(declaration.includesKey(key), {
				var checkType;
				var checkValue;
				tempVal = VTMValue.makeFromProperties(val);
				//is type strict? true by default
				checkType = val[\strictType] ? true;
				if(checkType, {
					if(tempVal.isValidType(declaration[key]).not, {
						Error("Parameter value '%' must be of type '%' value: %"
							.format(key, tempVal.type,
								tempVal.value.asCompileString))
							.throw;
					});
				});
				//check if value is e.g. within described range.
				checkValue = val[\strictValid] ? false;
				if(checkValue, {
					if(tempVal.isValidValue(declaration[key]).not, {
						Error("Parameter value '%' is invalid"
							.format(key)).throw;
					});
				});
				tempVal.value = declaration[key];
//				if(tempVal.value != declaration[key], {
//					("%[%] - Parameter value was changed by value object:".format(
//					name, this.class)	++
//					"\n\tfrom: '%'[%] \n\tto: '%'[%]".format(
//						declaration[key], declaration[key].class,
//						tempVal.value, tempVal.value.class
//					)).warn;
//				});
				paramDecl.put(key, tempVal.value);
			}, {
				var optional;
				//if not check if it is optional, true by default
				optional = val[\optional] ? true;
				if(optional.not, {
					Error("Parameters is missing non-optional value '%'"
						.format(key)).throw;
				}, {
					//otherwise use the default value for the parameter
					//decription.
					paramDecl.put(key, VTMValue.makeFromProperties(val));
				});
			});
		});
		parameters = VTMParameters.newFrom(paramDecl);
	}

	disable{
		this.disableForwarding;
		this.disableOSC;
	}

	enable{
		this.enableForwarding;
		this.enableOSC;
	}

	free{
		this.disableOSC;
		this.changed(\freed);
		this.releaseDependants;
		this.release;
		manager = nil;
	}

	addForwarding{arg key, addr, path, vtmJson = false;
		this.subclassResponsibility(thisMethod);
	}

	removeForwarding{arg key;
		this.subclassResponsibility(thisMethod);
	}

	removeAllForwardings{
		this.subclassResponsibility(thisMethod);
	}

	enableForwarding{
		this.subclassResponsibility(thisMethod);
	}

	disableForwarding{
		this.subclassResponsibility(thisMethod);
	}

	*parameterKeys{
		^this.parameterDescriptions.keys;
	}

	*parameterDescriptions{
		^VTMOrderedIdentityDictionary[
			\name -> (type: \string, optional: false),
			\path -> (type: \string, optional: true)
		]; 
	} 

	*mandatoryParameters{
		var result = [];
		this.parameterDescriptions.keysValuesDo({arg key, desc;
			if(desc.includesKey(\optional) and: {
				desc[\optional].not
			}, {
				result = result.add(key);
			});
		});
		^result;
	}

	description{
		var result = VTMOrderedIdentityDictionary[
			\parameters -> this.class.parameterDescriptions,
		];
		^result;
	}
	
	parameters{
		^parameters.copy;
	}

	declaration{
		this.subclassResponsibility(thisMethod);
	}

	makeView{arg parent, bounds, definition, settings;
		var viewClass = this.class.viewClassSymbol.asClass;
		//override class if defined in settings.
		if(settings.notNil, {
			if(settings.includesKey(\viewClass), {
				viewClass = settings[\viewClass];
			});
		});
		^viewClass.new(parent, bounds, definition, settings, this);
	}

	fullPath{
		^(this.path ++ this.leadingSeparator ++ this.name).asSymbol;
	}

	path{
		if(manager.isNil, {
			^path;
		}, {
			^manager.fullPath;
		});
	}

	path_{arg newPath;
		path = newPath;
		//rebuild OSC responders if they are running
		if(oscInterface.enabled, {
			this.disableOSC;
			this.enableOSC;
		});
	}

	hasDerivedPath{
		^manager.notNil;
	}

	get{arg key;
		^parameters.at(key);
	}

	leadingSeparator{ ^'/'; }

	enableOSC {
		oscInterface !? { oscInterface.enable(); };
		oscInterface ?? { oscInterface = VTMOSCInterface(this).enable() };
	}


	disableOSC {
		oscInterface !? { oscInterface.free() };
		oscInterface = nil;
	}

	oscEnabled {
		^oscInterface.notNil();
	}
}
