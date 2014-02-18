package akka.dynamo_mini;

/**
 * Implementation of virtual node in Amazon Dynamo research paper.
 *
 * @author: Gihan Karunarathne
 * Date: 1/4/14
 * Time: 6:04 PM
 * @email: gckarunarathne@gmail.com
 */

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.MemberUp;
import akka.contrib.pattern.DistributedPubSubExtension;
import akka.contrib.pattern.DistributedPubSubMediator;
import akka.dynamo_mini.coordination.StateMachine;
import akka.dynamo_mini.node_management.ConsistentHash;
import akka.dynamo_mini.node_management.HashFunction;
import akka.dynamo_mini.protocol.StateMachineProtocols.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.ArrayList;

import static akka.dynamo_mini.protocol.BootstraperProtocols.*;
import static akka.dynamo_mini.protocol.ClientProtocols.ReadRequest;
import static akka.dynamo_mini.protocol.ClientProtocols.WriteRequest;
import static akka.dynamo_mini.protocol.VirtualNodeProtocols.PutKeyValue;

public class VirtualNode extends UntypedActor {
    String nodeName = "";
    int numReplicas = Commons.numReplicas;
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    Cluster cluster = Cluster.get(getContext().system());
    ActorRef bootstraperRef, virtualNode;
    ActorSelection bootstraper;
    boolean isBootstraperUp = false;
    private ActorRef mediator = DistributedPubSubExtension.get(getContext().system()).mediator();

    /**
     * Store the preference list of other virtual nodes
     */
    ConsistentHash<ActorRef> ringManager;


    {
        // subscribe to the topic named "content"
        mediator.tell(new DistributedPubSubMediator.Subscribe("dynamo_mini_bootstraper", getSelf()), getSelf());
    }

    //subscribe to cluster changes, MemberUp
    @Override
    public void preStart() {
        cluster.subscribe(getSelf(), MemberUp.class);
        virtualNode = getSelf();
        nodeName = self().path().name();
        Address address = cluster.selfAddress();
        System.out.println("Virtual Node : " + nodeName + " is up @ " + address.protocol() + " : " + address.hostPort());
        bootstraper = getContext().actorSelection(address.protocol() + "://" + address.hostPort() + "/user/bootstraper");
        bootstraper.tell(new Identify(nodeName), virtualNode);
        ringManager = new ConsistentHash<>(new HashFunction(), numReplicas, new ArrayList<ActorRef>());
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public void onReceive(Object msg) {
        /*****************************************************
         * Read write requests form the state machines
         *****************************************************/
        if (msg instanceof QuorumReadRequest) {

        } else if (msg instanceof QuorumWriteRequest) {

        }
        /*****************************************************
         * Client Requests to the Coordinator
         *****************************************************/
        if (msg instanceof ReadRequest) { // most frequent request
            ReadRequest readRequest = (ReadRequest) msg;
            log.info("Read Request -- ( " + readRequest.getKey() + ")");
            /**
             * If this is the node responsible for handling the request, then process.
             * Otherwise forward to the relevant node (coordinator).
             */
            ActorRef coordinator = ringManager.get(readRequest.getKey());
            if (coordinator.path().name().equals(virtualNode.path().name())) {
                ActorRef stateMachine = getContext().actorOf(Props.create(StateMachine.class));
                /**
                 * Send Preference List to the State machine
                 */

            } else {
                log.info("Forward write request to :" + nodeName + " from " + coordinator.path());
                coordinator.forward(msg, getContext());
            }
        } else if (msg instanceof WriteRequest) {
            WriteRequest writeRequest = (WriteRequest) msg;
            log.info("Write Request -- ( " + writeRequest.getKey() + "," + writeRequest.getObjectValue() + " )");
            /**
             * If this is the node responsible for handling the request, then process.
             * Otherwise forward to the relevant node (coordinator).
             */
            ActorRef coordinator = ringManager.get(writeRequest.getKey());
            if (coordinator.path().name().equals(virtualNode.path().name())) {
                ActorRef stateMachine = getContext().actorOf(Props.create(StateMachine.class, getSelf(),
                        ringManager.getPreferenceList(writeRequest.getKey())));
                /**
                 * Send Preference List to the State machine
                 */

            } else {
                log.info("Forward write request to :" + nodeName + " from " + coordinator.path());
                coordinator.forward(msg, getContext());
            }
        }
        /*****************************************************
         * Virtual Node steps
         *****************************************************/
        else if (msg instanceof PutKeyValue) {
            PutKeyValue putKeyValue = (PutKeyValue) msg;

        }
        /**************************************************
         * Bootstrap steps
         **************************************************/
        else if (msg instanceof DistributedPubSubMediator.SubscribeAck) {
            log.info("subscribing " + nodeName);
            // Bootstraper is running. Ask to send join to ring to current virtual nodes in the ring.
            log.info("Ask for join to the ring from bootstraper");
            bootstraper.tell(new JoinToRing(nodeName), getSelf());

        } else if (msg instanceof AddNewNodeToRing) {
            AddNewNodeToRing addNewNodeToRing = (AddNewNodeToRing) msg;
            ringManager.add(addNewNodeToRing.getActorRef());

            /**
             * Send own details to the new node
             */
            addNewNodeToRing.getActorRef().tell(new CurrentRingNode(nodeName, getSelf()), getSelf());
        } else if (msg instanceof ActorIdentity) {
            ActorIdentity identity = (ActorIdentity) msg;
            log.info("Bootstraper replied with :" + identity.correlationId() + " to node:" + nodeName);
            if (identity.correlationId().equals(nodeName)) {
                /** Not Working. Need to check.
                 Res: http://doc.akka.io/docs/akka/snapshot/java/untyped-actors.html#actorselection-java
                 */
                ActorRef ref = identity.getRef();
                if (ref == null) {
                    getContext().stop(getSelf());
                } else {
                    bootstraperRef = ref;
                    getContext().watch(bootstraperRef);
                    log.info(nodeName + " send ACK to Bootstraper: " + ref.path().name());
                    virtualNode.tell("ACK by " + nodeName, getSelf());
                }
                isBootstraperUp = true;
            }

        } else if (msg instanceof ACKJoinToRing) {
            ACKJoinToRing ackJoinToRing = (ACKJoinToRing) msg;
            numReplicas = ackJoinToRing.getNumReplicas();
            log.info(nodeName + " got ACK from bootstraper");
            ringManager.add(getSelf());

        }
        /**
         * Details of a virtual node which is already in the ring.
         */
        else if (msg instanceof CurrentRingNode) {
            CurrentRingNode currentRingNode = (CurrentRingNode) msg;
            log.info("Add node " + currentRingNode.getActorRef().path().name() + " to the ring in " + nodeName);
            ringManager.add(currentRingNode.getActorRef());

        } else if (msg instanceof Terminated) {
            final Terminated t = (Terminated) msg;
            ActorRef actor = t.getActor();
            log.info("Actor: " + actor.path() + " terminated. Detect by VN: " + nodeName);
            if (t.getActor().equals(bootstraperRef)) {
                getContext().stop(getSelf());
            }

        } else if (msg instanceof Test) {

            Test data = (Test) msg;

            System.out.println("Actor : " + nodeName + " message: " + data.getMsg());

        } else {
            unhandled(msg);
        }

        /*if (msg instanceof TransformationJob) {
            TransformationJob job = (TransformationJob) msg;
            getSender()
                    .tell(new TransformationResult(job.getText().toUpperCase()),
                            getSelf());

        } else if (msg instanceof CurrentClusterState) {
            CurrentClusterState state = (CurrentClusterState) msg;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    register(member);
                }
            }

        } else if (msg instanceof MemberUp) {
            MemberUp mUp = (MemberUp) msg;
            register(mUp.member());

        } else {
            unhandled(msg);
        }

        if ((msg instanceof TransformationJob) && backends.isEmpty()) {
            TransformationJob job = (TransformationJob) msg;
            getSender().tell(
                    new JobFailed("Service unavailable, try again later", job),
                    getSender());

        } else if (msg instanceof TransformationJob) {
            TransformationJob job = (TransformationJob) msg;
            jobCounter++;
            backends.get(jobCounter % backends.size())
                    .forward(job, getContext());

        } else if (msg.equals(BACKEND_REGISTRATION)) {
            getContext().watch(getSender());
            backends.add(getSender());

        } else if (msg instanceof Terminated) {
            Terminated terminated = (Terminated) msg;
            backends.remove(terminated.getActor());

        } else {
            unhandled(msg);
        }*/
    }

}
