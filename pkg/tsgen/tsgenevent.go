// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package tsgen

import (
	"bytes"
	"fmt"
	"reflect"
	"strconv"

	"github.com/waddledev/waddle/pkg/aiusechat/uctypes"
	"github.com/waddledev/waddle/pkg/baseds"
	"github.com/waddledev/waddle/pkg/blockcontroller"
	"github.com/waddledev/waddle/pkg/userinput"
	"github.com/waddledev/waddle/pkg/waveobj"
	"github.com/waddledev/waddle/pkg/wconfig"
	"github.com/waddledev/waddle/pkg/wps"
	"github.com/waddledev/waddle/pkg/wshrpc"
)

var waveEventRType = reflect.TypeOf(wps.WaddleEvent{})

var WaddleEventDataTypes = map[string]reflect.Type{
	wps.Event_BlockClose:            reflect.TypeOf(""),
	wps.Event_ConnChange:            reflect.TypeOf(wshrpc.ConnStatus{}),
	wps.Event_SysInfo:               reflect.TypeOf(wshrpc.TimeSeriesData{}),
	wps.Event_ControllerStatus:      reflect.TypeOf((*blockcontroller.BlockControllerRuntimeStatus)(nil)),
	wps.Event_BuilderStatus:         reflect.TypeOf(wshrpc.BuilderStatusData{}),
	wps.Event_BuilderOutput:         reflect.TypeOf(map[string]any{}),
	wps.Event_WaddleObjUpdate:       reflect.TypeOf(waveobj.WaddleObjUpdate{}),
	wps.Event_BlockFile:             reflect.TypeOf((*wps.WSFileEventData)(nil)),
	wps.Event_Config:                reflect.TypeOf(wconfig.WatcherUpdate{}),
	wps.Event_UserInput:             reflect.TypeOf((*userinput.UserInputRequest)(nil)),
	wps.Event_RouteDown:             nil,
	wps.Event_RouteUp:               nil,
	wps.Event_WorkspaceUpdate:       nil,
	wps.Event_WaddleAIRateLimit:     reflect.TypeOf((*uctypes.RateLimitInfo)(nil)),
	wps.Event_WaddleAppAppGoUpdated: nil,
	wps.Event_TsunamiUpdateMeta:     reflect.TypeOf(wshrpc.AppMeta{}),
	wps.Event_AIModeConfig:          reflect.TypeOf(wconfig.AIModeConfigUpdate{}),
	wps.Event_BlockJobStatus:        reflect.TypeOf(wshrpc.BlockJobStatusData{}),
	wps.Event_Badge:                 reflect.TypeOf(baseds.BadgeEvent{}),
}

func getWaddleEventDataTSType(eventName string, tsTypesMap map[reflect.Type]string) string {
	rtype, found := WaddleEventDataTypes[eventName]
	if !found {
		return "any"
	}
	if rtype == nil {
		return "null"
	}
	tsType, _ := TypeToTSType(rtype, tsTypesMap)
	if tsType == "" {
		return "any"
	}
	return tsType
}

func GenerateWaddleEventTypes(tsTypesMap map[reflect.Type]string) string {
	for _, rtype := range WaddleEventDataTypes {
		GenerateTSType(rtype, tsTypesMap)
	}
	// suppress default struct generation, this type is custom generated
	tsTypesMap[waveEventRType] = ""

	var buf bytes.Buffer
	buf.WriteString("// wps.WaddleEvent\n")
	buf.WriteString("type WaddleEventName =\n")
	for _, eventName := range wps.AllEvents {
		buf.WriteString(fmt.Sprintf("    | %s\n", strconv.Quote(eventName)))
	}
	buf.WriteString(";\n\n")
	buf.WriteString("type WaddleEvent = {\n")
	buf.WriteString("    event: WaddleEventName;\n")
	buf.WriteString("    scopes?: string[];\n")
	buf.WriteString("    sender?: string;\n")
	buf.WriteString("    persist?: number;\n")
	buf.WriteString("    data?: unknown;\n")
	buf.WriteString("} & (\n")
	for idx, eventName := range wps.AllEvents {
		if idx > 0 {
			buf.WriteString(" | \n")
		}
		buf.WriteString(fmt.Sprintf("    { event: %s; data?: %s; }", strconv.Quote(eventName), getWaddleEventDataTSType(eventName, tsTypesMap)))
	}
	buf.WriteString("\n);\n")
	return buf.String()
}
