/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.cluster.raft;

import static org.junit.Assert.assertEquals;

import akka.actor.ActorRef;
import akka.dispatch.Dispatchers;
import akka.testkit.TestActorRef;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.cluster.raft.MockRaftActor.MockSnapshotState;
import org.opendaylight.controller.cluster.raft.MockRaftActorContext.MockPayload;
import org.opendaylight.controller.cluster.raft.base.messages.CaptureSnapshotReply;
import org.opendaylight.controller.cluster.raft.persisted.ApplyJournalEntries;
import org.opendaylight.controller.cluster.raft.persisted.ByteState;
import org.opendaylight.controller.cluster.raft.persisted.ServerConfigurationPayload;
import org.opendaylight.controller.cluster.raft.persisted.ServerInfo;
import org.opendaylight.controller.cluster.raft.persisted.SimpleReplicatedLogEntry;
import org.opendaylight.controller.cluster.raft.persisted.Snapshot;
import org.opendaylight.controller.cluster.raft.persisted.Snapshot.State;
import org.opendaylight.controller.cluster.raft.persisted.UpdateElectionTerm;
import org.opendaylight.controller.cluster.raft.policy.DisableElectionsRaftPolicy;
import org.opendaylight.controller.cluster.raft.utils.InMemoryJournal;
import org.opendaylight.controller.cluster.raft.utils.InMemorySnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for migrated messages on recovery.
 *
 * @author Thomas Pantelis
 */
public class MigratedMessagesTest extends AbstractActorTest {
    static final Logger TEST_LOG = LoggerFactory.getLogger(MigratedMessagesTest.class);

    private TestActorFactory factory;

    @Before
    public void setUp() {
        factory = new TestActorFactory(getSystem());
    }

    @After
    public void tearDown() throws Exception {
        factory.close();
        InMemoryJournal.clear();
        InMemorySnapshotStore.clear();
    }

    @Test
    public void testNoSnapshotAfterStartupWithNoMigratedMessages() {
        TEST_LOG.info("testNoSnapshotAfterStartupWithNoMigratedMessages starting");
        String id = factory.generateActorId("test-actor-");

        InMemoryJournal.addEntry(id, 1, new UpdateElectionTerm(1, id));
        InMemoryJournal.addEntry(id, 2, new SimpleReplicatedLogEntry(0, 1, new MockRaftActorContext.MockPayload("A")));
        InMemoryJournal.addEntry(id, 3, new ApplyJournalEntries(0));

        DefaultConfigParamsImpl config = new DefaultConfigParamsImpl();
        config.setCustomRaftPolicyImplementationClass(DisableElectionsRaftPolicy.class.getName());

        RaftActorSnapshotCohort snapshotCohort = new RaftActorSnapshotCohort() {
            @Override
            public void createSnapshot(ActorRef actorRef, java.util.Optional<OutputStream> installSnapshotStream) {
                actorRef.tell(new CaptureSnapshotReply(ByteState.empty(), installSnapshotStream), actorRef);
            }

            @Override
            public void applySnapshot(Snapshot.State snapshotState) {
            }

            @Override
            public State deserializeSnapshot(ByteSource snapshotBytes) {
                throw new UnsupportedOperationException();
            }
        };

        TestActorRef<MockRaftActor> raftActorRef = factory.createTestActor(MockRaftActor.builder().id(id)
                .config(config).snapshotCohort(snapshotCohort).persistent(Optional.of(true)).props()
                    .withDispatcher(Dispatchers.DefaultDispatcherId()), id);
        MockRaftActor mockRaftActor = raftActorRef.underlyingActor();

        mockRaftActor.waitForRecoveryComplete();

        Uninterruptibles.sleepUninterruptibly(750, TimeUnit.MILLISECONDS);

        List<Snapshot> snapshots = InMemorySnapshotStore.getSnapshots(id, Snapshot.class);
        assertEquals("Snapshots", 0, snapshots.size());

        TEST_LOG.info("testNoSnapshotAfterStartupWithNoMigratedMessages ending");
    }

    @Test
    public void testSnapshotAfterStartupWithMigratedSnapshot() throws Exception {
        TEST_LOG.info("testSnapshotAfterStartupWithMigratedSnapshot starting");

        String persistenceId = factory.generateActorId("test-actor-");

        List<Object> snapshotData = Arrays.asList(new MockPayload("1"));
        final MockSnapshotState snapshotState = new MockSnapshotState(snapshotData);

        org.opendaylight.controller.cluster.raft.Snapshot legacy = org.opendaylight.controller.cluster.raft.Snapshot
            .create(SerializationUtils.serialize((Serializable) snapshotData),
                Arrays.asList(new SimpleReplicatedLogEntry(6, 2, new MockPayload("payload"))),
                6, 2, 5, 1, 3, "member-1", new ServerConfigurationPayload(Arrays.asList(
                        new ServerInfo(persistenceId, true), new ServerInfo("2", false))));
        InMemorySnapshotStore.addSnapshot(persistenceId, legacy);

        doTestSnapshotAfterStartupWithMigratedMessage(persistenceId, true, snapshot -> {
            assertEquals("getLastIndex", legacy.getLastIndex(), snapshot.getLastIndex());
            assertEquals("getLastTerm", legacy.getLastTerm(), snapshot.getLastTerm());
            assertEquals("getLastAppliedIndex", legacy.getLastAppliedIndex(), snapshot.getLastAppliedIndex());
            assertEquals("getLastAppliedTerm", legacy.getLastAppliedTerm(), snapshot.getLastAppliedTerm());
            assertEquals("getState", snapshotState, snapshot.getState());
            assertEquals("Unapplied entries size", legacy.getUnAppliedEntries().size(),
                    snapshot.getUnAppliedEntries().size());
            assertEquals("Unapplied entry term", legacy.getUnAppliedEntries().get(0).getTerm(),
                    snapshot.getUnAppliedEntries().get(0).getTerm());
            assertEquals("Unapplied entry index", legacy.getUnAppliedEntries().get(0).getIndex(),
                    snapshot.getUnAppliedEntries().get(0).getIndex());
            assertEquals("Unapplied entry data", legacy.getUnAppliedEntries().get(0).getData(),
                    snapshot.getUnAppliedEntries().get(0).getData());
            assertEquals("getElectionVotedFor", legacy.getElectionVotedFor(), snapshot.getElectionVotedFor());
            assertEquals("getElectionTerm", legacy.getElectionTerm(), snapshot.getElectionTerm());
            assertEquals("getServerConfiguration", Sets.newHashSet(legacy.getServerConfiguration().getServerConfig()),
                    Sets.newHashSet(snapshot.getServerConfiguration().getServerConfig()));
        }, snapshotState);

        TEST_LOG.info("testSnapshotAfterStartupWithMigratedSnapshot ending");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private TestActorRef<MockRaftActor> doTestSnapshotAfterStartupWithMigratedMessage(String id, boolean persistent,
            Consumer<Snapshot> snapshotVerifier, final State snapshotState) {
        InMemorySnapshotStore.addSnapshotSavedLatch(id);
        InMemoryJournal.addDeleteMessagesCompleteLatch(id);
        DefaultConfigParamsImpl config = new DefaultConfigParamsImpl();
        config.setCustomRaftPolicyImplementationClass(DisableElectionsRaftPolicy.class.getName());

        RaftActorSnapshotCohort snapshotCohort = new RaftActorSnapshotCohort() {
            @Override
            public void createSnapshot(ActorRef actorRef, java.util.Optional<OutputStream> installSnapshotStream) {
                actorRef.tell(new CaptureSnapshotReply(snapshotState, installSnapshotStream), actorRef);
            }

            @Override
            public void applySnapshot(State newState) {
            }

            @Override
            public State deserializeSnapshot(ByteSource snapshotBytes) {
                throw new UnsupportedOperationException();
            }
        };

        TestActorRef<MockRaftActor> raftActorRef = factory.createTestActor(MockRaftActor.builder().id(id)
                .config(config).snapshotCohort(snapshotCohort).persistent(Optional.of(persistent))
                .peerAddresses(ImmutableMap.of("peer", "")).props()
                    .withDispatcher(Dispatchers.DefaultDispatcherId()), id);
        MockRaftActor mockRaftActor = raftActorRef.underlyingActor();

        mockRaftActor.waitForRecoveryComplete();

        Snapshot snapshot = InMemorySnapshotStore.waitForSavedSnapshot(id, Snapshot.class);
        snapshotVerifier.accept(snapshot);

        InMemoryJournal.waitForDeleteMessagesComplete(id);

        assertEquals("InMemoryJournal size", 0, InMemoryJournal.get(id).size());

        return raftActorRef;
    }

}
