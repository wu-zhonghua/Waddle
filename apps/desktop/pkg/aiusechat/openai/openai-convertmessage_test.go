// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package openai

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/waddledev/waddle/pkg/aiusechat/chatstore"
	"github.com/waddledev/waddle/pkg/aiusechat/uctypes"
	"github.com/waddledev/waddle/pkg/wavebase"
	"github.com/waddledev/waddle/pkg/web/sse"
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

func TestRunOpenAIChatStepReadsWaveRateLimitHeader(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Wave-RateLimit", "req=10,reqlimit=20,preq=0,preqlimit=5,reset=123")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	chatId := "openai-wave-rate-limit"
	chatstore.DefaultChatStore.Delete(chatId)
	defer chatstore.DefaultChatStore.Delete(chatId)
	config := uctypes.AIOptsType{
		Provider: uctypes.AIProvider_Waddle,
		APIType:  uctypes.APIType_OpenAIResponses,
		Model:    "gpt-5-mini",
		Endpoint: server.URL,
	}
	msg := &OpenAIChatMessage{
		MessageId: "message-1",
		Message: &OpenAIMessage{
			Role:    "user",
			Content: []OpenAIMessageContent{{Type: "input_text", Text: "hello"}},
		},
	}
	if err := chatstore.DefaultChatStore.PostMessage(chatId, &config, msg); err != nil {
		t.Fatalf("seed chat: %v", err)
	}

	ctx := context.Background()
	handler := sse.MakeSSEHandlerCh(httptest.NewRecorder(), ctx)
	stopReason, _, rateLimit, err := RunOpenAIChatStep(ctx, handler, uctypes.WaddleChatOpts{
		ClientId: "client-1",
		ChatId:   chatId,
		Config:   config,
	}, nil)
	if err != nil {
		t.Fatalf("run chat step: %v", err)
	}
	if stopReason == nil || stopReason.Kind != uctypes.StopKindPremiumRateLimit {
		t.Fatalf("expected premium rate limit stop reason, got %#v", stopReason)
	}
	if rateLimit == nil || rateLimit.Req != 10 || rateLimit.ReqLimit != 20 || rateLimit.PReq != 0 || rateLimit.PReqLimit != 5 || rateLimit.ResetEpoch != 123 {
		t.Fatalf("unexpected rate limit info: %#v", rateLimit)
	}
}
