package akka.dynamo_mini.protocol;

import akka.actor.ActorRef;

import java.io.Serializable;

/**
 * Class Description.
 *
 * @author: Gihan Karunarathne
 * Date: 1/20/14
 * Time: 9:01 PM
 * @email: gckarunarathne@gmail.com
 */
public interface BootstraperProtocols {

    public static class JoinToRing implements Serializable {
        private final String nodeName;

        public JoinToRing(String key) {
            this.nodeName = key;
        }

        public String getNodeName() {
            return this.nodeName;
        }
    }

    public static class AddNewNodeToRing implements Serializable {
        private final String nodeName;
        private final ActorRef ref;

        public AddNewNodeToRing(String key,ActorRef ref) {
            this.nodeName = key;
            this.ref = ref;
        }

        public String getNodeName() {
            return this.nodeName;
        }

        public ActorRef getActorRef() {
            return this.ref;
        }
    }

    public static class ACKJoinToRing implements Serializable {
        private final String nodeName;
        private final int numReplicas;

        public ACKJoinToRing(String key, int numReplicas) {
            this.nodeName = key;
            this.numReplicas = numReplicas;
        }

        public String getNodeName() {
            return this.nodeName;
        }

        public int getNumReplicas() {
            return this.numReplicas;
        }
    }

    public static class CurrentRingNode implements Serializable {
        private final String nodeName;
        private final ActorRef ref;

        public CurrentRingNode(String key, ActorRef ref) {
            this.nodeName = key;
            this.ref = ref;
        }

        public String getNodeName() {
            return this.nodeName;
        }

        public ActorRef getActorRef() {
            return this.ref;
        }
    }
}