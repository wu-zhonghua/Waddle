// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshserver

import (
	"context"
	"testing"
	"time"
)

func TestConnCommandContextIgnoresParentDeadline(t *testing.T) {
	parentCtx, cancelFn := context.WithTimeout(context.Background(), time.Nanosecond)
	defer cancelFn()
	<-parentCtx.Done()

	connCtx := connCommandContext(parentCtx)

	if deadline, ok := connCtx.Deadline(); ok {
		t.Fatalf("connection command context should not inherit parent deadline, got %v", deadline)
	}
	select {
	case <-connCtx.Done():
		t.Fatal("connection command context should not be canceled by parent deadline")
	default:
	}
	if err := connCtx.Err(); err != nil {
		t.Fatalf("connection command context should ignore parent deadline error, got %v", err)
	}
}
