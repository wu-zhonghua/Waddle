// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package tsgen

import (
	"reflect"
	"strings"
	"testing"

	"github.com/waddledev/waddle/pkg/wps"
	"github.com/waddledev/waddle/pkg/wshrpc"
)

func TestGenerateWaddleEventTypes(t *testing.T) {
	tsTypesMap := make(map[reflect.Type]string)
	waddleEventTypeDecl := GenerateWaddleEventTypes(tsTypesMap)

	if !strings.Contains(waddleEventTypeDecl, "type WaddleEventName =\n") ||
		!strings.Contains(waddleEventTypeDecl, `    | "blockclose"`) {
		t.Fatalf("expected WaddleEventName declaration, got:\n%s", waddleEventTypeDecl)
	}
	if !strings.Contains(waddleEventTypeDecl, `{ event: "block:jobstatus"; data?: BlockJobStatusData; }`) {
		t.Fatalf("expected typed block:jobstatus event, got:\n%s", waddleEventTypeDecl)
	}
	if !strings.Contains(waddleEventTypeDecl, `{ event: "route:up"; data?: null; }`) {
		t.Fatalf("expected null for known no-data event, got:\n%s", waddleEventTypeDecl)
	}
	if got := getWaddleEventDataTSType("unmapped:event", tsTypesMap); got != "any" {
		t.Fatalf("expected any for unmapped event fallback, got: %q", got)
	}
	if _, found := tsTypesMap[reflect.TypeOf(wps.WaddleEvent{})]; !found {
		t.Fatalf("expected WaddleEvent type to be seeded in tsTypesMap")
	}
	if _, found := tsTypesMap[reflect.TypeOf(wshrpc.BlockJobStatusData{})]; !found {
		t.Fatalf("expected mapped data types to be generated into tsTypesMap")
	}
}
