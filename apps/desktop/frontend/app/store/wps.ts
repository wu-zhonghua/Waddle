// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { WshClient } from "@/app/store/wshclient";
import { RpcApi } from "@/app/store/wshclientapi";
import { isPreviewWindow } from "@/app/store/windowtype";
import { isBlank } from "@/util/util";
import { Subject } from "rxjs";

let WpsRpcClient: WshClient;

function setWpsRpcClient(client: WshClient) {
    WpsRpcClient = client;
}

type WaddleEventSubject<T extends WaddleEventName = WaddleEventName> = {
    handler: (event: Extract<WaddleEvent, { event: T }>) => void;
    scope?: string;
};

type WaddleEventSubjectContainer = {
    handler: (event: WaddleEvent) => void;
    scope?: string;
    id: string;
};

type WaddleEventSubscription<T extends WaddleEventName = WaddleEventName> = WaddleEventSubject<T> & {
    eventType: T;
};

type WaddleEventUnsubscribe = {
    id: string;
    eventType: string;
};

// key is "eventType" or "eventType|oref"
const fileSubjects = new Map<string, SubjectWithRef<WSFileEventData>>();
const waveEventSubjects = new Map<string, WaddleEventSubjectContainer[]>();

function wpsReconnectHandler() {
    for (const eventType of waveEventSubjects.keys()) {
        updateWaddleEventSub(eventType);
    }
}

function updateWaddleEventSub(eventType: string) {
    if (isPreviewWindow()) {
        return;
    }
    const subjects = waveEventSubjects.get(eventType);
    if (subjects == null) {
        RpcApi.EventUnsubCommand(WpsRpcClient, eventType, { noresponse: true });
        return;
    }
    const subreq: SubscriptionRequest = { event: eventType, scopes: [], allscopes: false };
    for (const scont of subjects) {
        if (isBlank(scont.scope)) {
            subreq.allscopes = true;
            subreq.scopes = [];
            break;
        }
        subreq.scopes.push(scont.scope);
    }
    RpcApi.EventSubCommand(WpsRpcClient, subreq, { noresponse: true });
}

function waveEventSubscribeSingle<T extends WaddleEventName>(subscription: WaddleEventSubscription<T>): () => void {
    // console.log("waveEventSubscribeSingle", subscription);
    if (subscription.handler == null) {
        return () => {};
    }
    const id: string = crypto.randomUUID();
    let subjects = waveEventSubjects.get(subscription.eventType);
    if (subjects == null) {
        subjects = [];
        waveEventSubjects.set(subscription.eventType, subjects);
    }
    const subcont: WaddleEventSubjectContainer = {
        id,
        handler: subscription.handler as (event: WaddleEvent) => void,
        scope: subscription.scope,
    };
    subjects.push(subcont);
    updateWaddleEventSub(subscription.eventType);
    return () => waveEventUnsubscribe({ id, eventType: subscription.eventType });
}

function waveEventUnsubscribe(...unsubscribes: WaddleEventUnsubscribe[]) {
    const eventTypeSet = new Set<string>();
    for (const unsubscribe of unsubscribes) {
        const subjects = waveEventSubjects.get(unsubscribe.eventType);
        if (subjects == null) {
            return;
        }
        const idx = subjects.findIndex((s) => s.id === unsubscribe.id);
        if (idx === -1) {
            return;
        }
        subjects.splice(idx, 1);
        if (subjects.length === 0) {
            waveEventSubjects.delete(unsubscribe.eventType);
        }
        eventTypeSet.add(unsubscribe.eventType);
    }

    for (const eventType of eventTypeSet) {
        updateWaddleEventSub(eventType);
    }
}

function getFileSubject(zoneId: string, fileName: string): SubjectWithRef<WSFileEventData> {
    const subjectKey = zoneId + "|" + fileName;
    let subject = fileSubjects.get(subjectKey);
    if (subject == null) {
        subject = new Subject<any>() as any;
        subject.refCount = 0;
        subject.release = () => {
            subject.refCount--;
            if (subject.refCount === 0) {
                subject.complete();
                fileSubjects.delete(subjectKey);
            }
        };
        fileSubjects.set(subjectKey, subject);
    }
    subject.refCount++;
    return subject;
}

function handleWaddleEvent(event: WaddleEvent) {
    // console.log("handleWaddleEvent", event);
    const subjects = waveEventSubjects.get(event.event);
    if (subjects == null) {
        return;
    }
    for (const scont of subjects) {
        if (isBlank(scont.scope)) {
            scont.handler(event);
            continue;
        }
        if (event.scopes == null) {
            continue;
        }
        if (event.scopes.includes(scont.scope)) {
            scont.handler(event);
        }
    }
}

export {
    getFileSubject,
    handleWaddleEvent,
    setWpsRpcClient,
    waveEventSubscribeSingle,
    waveEventUnsubscribe,
    wpsReconnectHandler,
};
