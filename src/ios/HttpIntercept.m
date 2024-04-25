/********* CDVCorHttpd.m Cordova Plugin Implementation *******/

#include <ifaddrs.h>
#include <arpa/inet.h>
#include <net/if.h>

#import <Cordova/CDV.h>

#import "DDLog.h"
#import "DDTTYLogger.h"
#import "HTTPServer.h"

@interface HttpIntercept : CDVPlugin {
    // Member variables go here.

}

@property (assign) int port;
@property (assign) BOOL localhost_only;

- (void)init:(CDVInvokedUrlCommand*)command;

@end

@implementation CorHttpd

- (void)pluginInitialize
{
    self.httpServer = nil;
    self.localPath = @"";
    self.url = @"";

    self.www_root = @"";
    self.port = 8888;
    self.localhost_only = false;
}

@end

