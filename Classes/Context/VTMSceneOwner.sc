VTMSceneOwner : VTMNetworkedContext {
	var <sceneFactory;

	*new{arg network, definition, declaration;
		^super.new('scenes', network, definition, declaration).initSceneOwner;
	}

	initSceneOwner{
		sceneFactory = VTMSceneFactory.new(this);
	}

	scenes{
		^children;
	}

	addScene{arg newScene;
	}

	loadSceneCue{arg cue;
		var newScene;
		try{
			newScene = sceneFactory.build(cue);
		} {|err|
			"Scene cue build error".warn;
			err.postln;
		};
		this.addScene(newScene);
	}
}
