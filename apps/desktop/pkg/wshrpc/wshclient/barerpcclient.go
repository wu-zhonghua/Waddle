// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshclient

import (
	"fmt"
	"sync"

	"github.com/google/uuid"
	"github.com/waddledev/waddle/pkg/wps"
	"github.com/waddledev/waddle/pkg/wshrpc"
	"github.com/waddledev/waddle/pkg/wshutil"
)

type WshServer struct{}

func (*WshServer) WshServerImpl() {}

var WshServerImpl = WshServer{}

var waveSrvClient_Singleton *wshutil.WshRpc
var waveSrvClient_Once = &sync.Once{}
var waveSrvClient_RouteId string

func GetBareRpcClient() *wshutil.WshRpc {
	waveSrvClient_Once.Do(func() {
		waveSrvClient_Singleton = wshutil.MakeWshRpc(wshrpc.RpcContext{}, &WshServerImpl, "bare-client")
		waveSrvClient_RouteId = fmt.Sprintf("bare:%s", uuid.New().String())
		// we can safely ignore the error from RegisterTrustedLeaf since the route is valid
		wshutil.DefaultRouter.RegisterTrustedLeaf(waveSrvClient_Singleton, waveSrvClient_RouteId)
		wps.Broker.SetClient(wshutil.DefaultRouter)
	})
	return waveSrvClient_Singleton
}

func GetBareRpcClientRouteId() string {
	GetBareRpcClient()
	return waveSrvClient_RouteId
}
