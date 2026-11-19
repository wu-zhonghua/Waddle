// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package anthropic

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

func TestBuildAnthropicHTTPRequestUsesWaveCloudHeaders(t *testing.T) {
	chatOpts := uctypes.WaddleChatOpts{
		ClientId: "client-1",
		ChatId:   "chat-1",
		Config: uctypes.AIOptsType{
			Provider: uctypes.AIProvider_Waddle,
			APIType:  uctypes.APIType_AnthropicMessages,
			Model:    "claude-sonnet-4-5",
			Endpoint: uctypes.DefaultAIEndpoint,
		},
	}
	req, err := buildAnthropicHTTPRequest(context.Background(), nil, chatOpts)
	if err != nil {
		t.Fatalf("build request: %v", err)
	}

	want := map[string]string{
		"X-Wave-ClientId":    "client-1",
		"X-Wave-ChatId":      "chat-1",
		"X-Wave-Version":     wavebase.WaddleVersion,
		"X-Wave-APIType":     uctypes.APIType_AnthropicMessages,
		"X-Wave-RequestType": "waveai",
	}
	assertWaveCloudHeaders(t, req.Header, want)
}

func TestBuildAnthropicHTTPRequestOmitsWaveCloudHeadersForDirectProvider(t *testing.T) {
	chatOpts := uctypes.WaddleChatOpts{
		ClientId: "client-1",
		ChatId:   "chat-1",
		Config: uctypes.AIOptsType{
			Provider: uctypes.AIProvider_Custom,
			APIType:  uctypes.APIType_AnthropicMessages,
			Model:    "claude-sonnet-4-5",
			Endpoint: "https://api.anthropic.com/v1/messages",
		},
	}
	req, err := buildAnthropicHTTPRequest(context.Background(), nil, chatOpts)
	if err != nil {
		t.Fatalf("build request: %v", err)
	}

	assertWaveCloudHeaders(t, req.Header, map[string]string{})
}

func TestRunAnthropicChatStepReadsWaveRateLimitHeader(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Wave-RateLimit", "req=10,reqlimit=20,preq=0,preqlimit=5,reset=123")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	chatId := "anthropic-wave-rate-limit"
	chatstore.DefaultChatStore.Delete(chatId)
	defer chatstore.DefaultChatStore.Delete(chatId)
	config := uctypes.AIOptsType{
		Provider: uctypes.AIProvider_Waddle,
		APIType:  uctypes.APIType_AnthropicMessages,
		Model:    "claude-sonnet-4-5",
		Endpoint: server.URL,
	}
	msg := &anthropicChatMessage{
		MessageId: "message-1",
		Role:      "user",
		Content:   []anthropicMessageContentBlock{{Type: "text", Text: "hello"}},
	}
	if err := chatstore.DefaultChatStore.PostMessage(chatId, &config, msg); err != nil {
		t.Fatalf("seed chat: %v", err)
	}

	ctx := context.Background()
	handler := sse.MakeSSEHandlerCh(httptest.NewRecorder(), ctx)
	stopReason, _, rateLimit, err := RunAnthropicChatStep(ctx, handler, uctypes.WaddleChatOpts{
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

func TestConvertPartsToAnthropicBlocks_TextOnly(t *testing.T) {
	parts := []uctypes.UIMessagePart{
		{Type: "text", Text: "Hello world"},
		{Type: "text", Text: "Default text"},
	}

	blocks, err := convertPartsToAnthropicBlocks(parts, "user")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(blocks) != 2 {
		t.Fatalf("expected 2 blocks, got %d", len(blocks))
	}

	// Check first block
	block1 := blocks[0]
	if block1.Type != "text" {
		t.Errorf("expected type 'text', got %v", block1.Type)
	}
	if block1.Text != "Hello world" {
		t.Errorf("expected text 'Hello world', got %v", block1.Text)
	}

	// Check second block (empty type defaults to text)
	block2 := blocks[1]
	if block2.Type != "text" {
		t.Errorf("expected type 'text', got %v", block2.Type)
	}
	if block2.Text != "Default text" {
		t.Errorf("expected text 'Default text', got %v", block2.Text)
	}
}

func TestConvertPartsToAnthropicBlocks_SkipsUnknownTypes(t *testing.T) {
	parts := []uctypes.UIMessagePart{
		{Type: "text", Text: "Valid text"},
		{Type: "unknown_type", Text: "Should be skipped"},
		{Type: "text", Text: "Another valid text"},
	}

	blocks, err := convertPartsToAnthropicBlocks(parts, "user")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(blocks) != 2 {
		t.Fatalf("expected 2 blocks (unknown type skipped), got %d", len(blocks))
	}

	block1 := blocks[0]
	if block1.Text != "Valid text" {
		t.Errorf("expected first text 'Valid text', got %v", block1.Text)
	}

	block2 := blocks[1]
	if block2.Text != "Another valid text" {
		t.Errorf("expected second text 'Another valid text', got %v", block2.Text)
	}
}

func TestGetFunctionCallInputByToolCallId(t *testing.T) {
	toolData := &uctypes.UIMessageDataToolUse{ToolCallId: "call-1", ToolName: "read_file", Status: uctypes.ToolUseStatusPending}
	chat := uctypes.AIChat{
		NativeMessages: []uctypes.GenAIMessage{
			&anthropicChatMessage{
				MessageId: "m1",
				Role:      "assistant",
				Content: []anthropicMessageContentBlock{
					{Type: "tool_use", ID: "call-1", Name: "read_file", Input: map[string]interface{}{"path": "/tmp/a"}, ToolUseData: toolData},
				},
			},
		},
	}
	fnCall := GetFunctionCallInputByToolCallId(chat, "call-1")
	if fnCall == nil {
		t.Fatalf("expected function call input")
	}
	if fnCall.CallId != "call-1" || fnCall.Name != "read_file" {
		t.Fatalf("unexpected function call input: %#v", fnCall)
	}
	if fnCall.Arguments != "{\"path\":\"/tmp/a\"}" {
		t.Fatalf("unexpected arguments: %s", fnCall.Arguments)
	}
	if fnCall.ToolUseData == nil || fnCall.ToolUseData.ToolCallId != "call-1" {
		t.Fatalf("expected tool use data")
	}
}

func TestUpdateAndRemoveToolUseCall(t *testing.T) {
	chatID := "anthropic-test-tooluse"
	chatstore.DefaultChatStore.Delete(chatID)
	defer chatstore.DefaultChatStore.Delete(chatID)

	aiOpts := &uctypes.AIOptsType{
		APIType:    uctypes.APIType_AnthropicMessages,
		Model:      "claude-sonnet-4-5",
		APIVersion: AnthropicDefaultAPIVersion,
	}
	msg := &anthropicChatMessage{
		MessageId: "m1",
		Role:      "assistant",
		Content: []anthropicMessageContentBlock{
			{Type: "text", Text: "start"},
			{Type: "tool_use", ID: "call-1", Name: "read_file", Input: map[string]interface{}{"path": "/tmp/a"}},
		},
	}
	if err := chatstore.DefaultChatStore.PostMessage(chatID, aiOpts, msg); err != nil {
		t.Fatalf("failed to seed chat: %v", err)
	}

	newData := uctypes.UIMessageDataToolUse{ToolCallId: "call-1", ToolName: "read_file", Status: uctypes.ToolUseStatusCompleted}
	if err := UpdateToolUseData(chatID, "call-1", newData); err != nil {
		t.Fatalf("update failed: %v", err)
	}

	chat := chatstore.DefaultChatStore.Get(chatID)
	updated := chat.NativeMessages[0].(*anthropicChatMessage)
	if updated.Content[1].ToolUseData == nil || updated.Content[1].ToolUseData.Status != uctypes.ToolUseStatusCompleted {
		t.Fatalf("tool use data not updated")
	}

	if err := RemoveToolUseCall(chatID, "call-1"); err != nil {
		t.Fatalf("remove failed: %v", err)
	}
	chat = chatstore.DefaultChatStore.Get(chatID)
	updated = chat.NativeMessages[0].(*anthropicChatMessage)
	if len(updated.Content) != 1 || updated.Content[0].Type != "text" {
		t.Fatalf("expected tool_use block removed, got %#v", updated.Content)
	}
}

func TestConvertToUIMessageIncludesToolUseData(t *testing.T) {
	msg := &anthropicChatMessage{
		MessageId: "m1",
		Role:      "assistant",
		Content: []anthropicMessageContentBlock{
			{
				Type:        "tool_use",
				ID:          "call-1",
				Name:        "read_file",
				Input:       map[string]interface{}{"path": "/tmp/a"},
				ToolUseData: &uctypes.UIMessageDataToolUse{ToolCallId: "call-1", ToolName: "read_file", Status: uctypes.ToolUseStatusPending},
			},
		},
	}
	ui := msg.ConvertToUIMessage()
	if ui == nil || len(ui.Parts) != 2 {
		t.Fatalf("expected tool and data-tooluse parts, got %#v", ui)
	}
	if ui.Parts[0].Type != "tool-read_file" || ui.Parts[1].Type != "data-tooluse" {
		t.Fatalf("unexpected part types: %#v", ui.Parts)
	}
}
