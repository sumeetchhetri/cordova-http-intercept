
var argscheck = require('cordova/argscheck'), exec = require('cordova/exec');
var httpIntercepts_exports = {};

httpIntercepts_exports.init = function(base, configs, success, error) {
	var opts = {
		base: base,
		resources: configs.resources,
		version: configs.version,
		versionCheckUrl: configs.versionCheckUrl,
		baseUrl: configs.baseUrl
	};
	exec(success, error, "HttpIntercept", "init", [ opts ]);
};

module.exports = httpIntercepts_exports;

/*
./emulator -avd Pixel_C_API_31 -writable-system
./platform-tools/adb devices
./platform-tools/adb root
./platform-tools/adb remount
./platform-tools/adb shell
*/
