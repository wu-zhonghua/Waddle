// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package openai

import (
	"context"
	"testing"

	"github.com/waddledev/waddle/pkg/aiusechat/uctypes"
	"github.com/waddledev/waddle/pkg/wavebase"
)

var waveCloudHeaderNames = []string{
	"X-Wave-ClientId",
	"X-Wave-ChatId",
	"X-Wave-Version",
	"X-Wave-APIType",
	"X-Wave-RequestType",
}

func TestBuildOpenAIHTTPRequestUsesWaveCloudHeaders(t *testing.T) {
	chatOpts := uctypes.WaddleChatOpts{
		ClientId: "client-1",
		ChatId:   "chat-1",
		Config: uctypes.AIOptsType{
			Provider: uctypes.AIProvider_Waddle,
			APIType:  uctypes.APIType_OpenAIResponses,
			Model:    "gpt-5-mini",
			Endpoint: uctypes.DefaultAIEndpoint,
		},
	}
	req, err := buildOpenAIHTTPRequest(context.Background(), nil, chatOpts, nil)
	if err != nil {
		t.Fatalf("build request: %v", err)
	}

	want := map[string]string{
		"X-Wave-ClientId":    "client-1",
		"X-Wave-ChatId":      "chat-1",
		"X-Wave-Version":     wavebase.WaddleVersion,
		"X-Wave-APIType":     uctypes.APIType_OpenAIResponses,
		"X-Wave-RequestType": "waveai",
	}
	for name, expected := range want {
		if actual := req.Header.Get(name); actual != expected {
			t.Errorf("header %s: expected %q, got %q", name, expected, actual)
		}
	}
	for _, name := range []string{"X-Waddle-ClientId", "X-Waddle-ChatId", "X-Waddle-Version", "X-Waddle-APIType", "X-Waddle-RequestType"} {
		if actual := req.Header.Get(name); actual != "" {
			t.Errorf("obsolete header %s was set to %q", name, actual)
		}
	}
}

func TestBuildOpenAIHTTPRequestOmitsWaveCloudHeadersForDirectProvider(t *testing.T) {
	chatOpts := uctypes.WaddleChatOpts{
		ClientId: "client-1",
		ChatId:   "chat-1",
		Config: uctypes.AIOptsType{
			Provider: uctypes.AIProvider_OpenAI,
			APIType:  uctypes.APIType_OpenAIResponses,
			Model:    "gpt-5-mini",
			Endpoint: "https://api.openai.com/v1/responses",
		},
	}
	req, err := buildOpenAIHTTPRequest(context.Background(), nil, chatOpts, nil)
	if err != nil {
		t.Fatalf("build request: %v", err)
	}

	for _, name := range waveCloudHeaderNames {
		if actual := req.Header.Get(name); actual != "" {
			t.Errorf("direct provider header %s was set to %q", name, actual)
		}
	}
}
