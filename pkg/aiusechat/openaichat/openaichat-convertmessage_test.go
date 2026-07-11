// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package openaichat

import (
	"context"
	"net/http"
	"testing"

	"github.com/waddledev/waddle/pkg/aiusechat/uctypes"
	"github.com/waddledev/waddle/pkg/wavebase"
)

func TestBuildChatHTTPRequestUsesWaveCloudHeaders(t *testing.T) {
	chatOpts := uctypes.WaddleChatOpts{
		ClientId: "client-1",
		ChatId:   "chat-1",
		Config: uctypes.AIOptsType{
			Provider: uctypes.AIProvider_Waddle,
			APIType:  uctypes.APIType_OpenAIChat,
			Model:    "gpt-5-mini",
			Endpoint: uctypes.DefaultAIEndpoint,
		},
	}
	req, err := buildChatHTTPRequest(context.Background(), nil, chatOpts)
	if err != nil {
		t.Fatalf("build request: %v", err)
	}

	want := map[string]string{
		"X-Wave-ClientId":    "client-1",
		"X-Wave-ChatId":      "chat-1",
		"X-Wave-Version":     wavebase.WaddleVersion,
		"X-Wave-APIType":     uctypes.APIType_OpenAIChat,
		"X-Wave-RequestType": "waveai",
	}
	assertWaveCloudHeaders(t, req.Header, want)
}

func TestBuildChatHTTPRequestOmitsWaveCloudHeadersForDirectProvider(t *testing.T) {
	chatOpts := uctypes.WaddleChatOpts{
		ClientId: "client-1",
		ChatId:   "chat-1",
		Config: uctypes.AIOptsType{
			Provider: uctypes.AIProvider_OpenAI,
			APIType:  uctypes.APIType_OpenAIChat,
			Model:    "gpt-4o",
			Endpoint: "https://api.openai.com/v1/chat/completions",
		},
	}
	req, err := buildChatHTTPRequest(context.Background(), nil, chatOpts)
	if err != nil {
		t.Fatalf("build request: %v", err)
	}

	assertWaveCloudHeaders(t, req.Header, map[string]string{})
}

func assertWaveCloudHeaders(t *testing.T, headers http.Header, want map[string]string) {
	t.Helper()
	for _, name := range []string{"X-Wave-ClientId", "X-Wave-ChatId", "X-Wave-Version", "X-Wave-APIType", "X-Wave-RequestType"} {
		if actual := headers.Get(name); actual != want[name] {
			t.Errorf("header %s: expected %q, got %q", name, want[name], actual)
		}
	}
	for _, name := range []string{"X-Waddle-ClientId", "X-Waddle-ChatId", "X-Waddle-Version", "X-Waddle-APIType", "X-Waddle-RequestType"} {
		if actual := headers.Get(name); actual != "" {
			t.Errorf("obsolete header %s was set to %q", name, actual)
		}
	}
}
