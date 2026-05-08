package io.veriguard.utils.helpers;

import io.veriguard.database.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class TagHelper {
  public static List<Tag> crawlAllAttackChainRunTags(AttackChainRun attackChainRun) {
    List<Tag> tags = new ArrayList<>(attackChainRun.getTags());
    tags.addAll(
        attackChainRun.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
    tags.addAll(
        attackChainRun.getTeams().stream()
            .flatMap(team -> team.getUsers().stream().flatMap(user -> user.getTags().stream()))
            .toList());
    tags.addAll(
        attackChainRun.getTeams().stream()
            .flatMap(
                team ->
                    team.getUsers().stream()
                        .flatMap(
                            user ->
                                user.getOrganization() == null
                                    ? null
                                    : user.getOrganization().getTags().stream()
                                        .filter(Objects::nonNull)))
            .toList());
    tags.addAll(
        attackChainRun.getDocuments().stream()
            .flatMap(document -> document.getTags().stream())
            .toList());
    tags.addAll(
        attackChainRun.getAttackChainNodes().stream()
            .flatMap(attackChainNode -> attackChainNode.getTags().stream())
            .toList());
    tags.addAll(
        attackChainRun.getAttackChainNodes().stream()
            .flatMap(
                attackChainNode ->
                    attackChainNode.getNodeContract().isPresent()
                        ? attackChainNode.getNodeContract().get().getPayload() != null
                            ? attackChainNode
                                .getNodeContract()
                                .get()
                                .getPayload()
                                .getTags()
                                .stream()
                            : Stream.of()
                        : Stream.of())
            .toList());
    return tags;
  }

  public static List<Tag> crawlAllAttackChainTags(AttackChain attackChain) {
    List<Tag> tags = new ArrayList<>(attackChain.getTags());
    tags.addAll(attackChain.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
    tags.addAll(
        attackChain.getTeams().stream()
            .flatMap(team -> team.getUsers().stream().flatMap(user -> user.getTags().stream()))
            .toList());
    tags.addAll(
        attackChain.getTeams().stream()
            .flatMap(
                team ->
                    team.getUsers().stream()
                        .flatMap(
                            user ->
                                user.getOrganization() == null
                                    ? null
                                    : user.getOrganization().getTags().stream()
                                        .filter(Objects::nonNull)))
            .toList());
    tags.addAll(
        attackChain.getDocuments().stream()
            .flatMap(document -> document.getTags().stream())
            .toList());
    tags.addAll(
        attackChain.getAttackChainNodes().stream()
            .flatMap(attackChainNode -> attackChainNode.getTags().stream())
            .toList());
    tags.addAll(
        attackChain.getAttackChainNodes().stream()
            .flatMap(
                attackChainNode ->
                    attackChainNode.getNodeContract().isPresent()
                        ? attackChainNode.getNodeContract().get().getPayload() != null
                            ? attackChainNode
                                .getNodeContract()
                                .get()
                                .getPayload()
                                .getTags()
                                .stream()
                            : Stream.of()
                        : Stream.of())
            .toList());
    return tags;
  }

  public static List<Tag> crawlAllAttackChainNodesTags(List<AttackChainNode> attackChainNodes) {
    List<Tag> tags =
        new ArrayList<>(
            attackChainNodes.stream()
                .flatMap(attackChainNode -> attackChainNode.getTags().stream())
                .toList());
    tags.addAll(
        attackChainNodes.stream()
            .flatMap(attackChainNode -> attackChainNode.getTeams().stream())
            .flatMap(team -> team.getTags().stream())
            .toList());
    tags.addAll(
        attackChainNodes.stream()
            .flatMap(attackChainNode -> attackChainNode.getTeams().stream())
            .flatMap(team -> team.getUsers().stream().flatMap(user -> user.getTags().stream()))
            .toList());
    tags.addAll(
        attackChainNodes.stream()
            .flatMap(attackChainNode -> attackChainNode.getTeams().stream())
            .flatMap(
                team ->
                    team.getUsers().stream()
                        .flatMap(
                            user ->
                                user.getOrganization() == null
                                    ? null
                                    : user.getOrganization().getTags().stream()
                                        .filter(Objects::nonNull)))
            .toList());
    tags.addAll(
        attackChainNodes.stream()
            .flatMap(attackChainNode -> attackChainNode.getDocuments().stream())
            .map(AttackChainNodeDocument::getDocument)
            .flatMap(document -> document.getTags().stream())
            .toList());
    tags.addAll(
        attackChainNodes.stream()
            .flatMap(attackChainNode -> attackChainNode.getTags().stream())
            .toList());
    tags.addAll(
        attackChainNodes.stream()
            .flatMap(
                attackChainNode ->
                    attackChainNode.getNodeContract().isPresent()
                        ? attackChainNode.getNodeContract().get().getPayload() != null
                            ? attackChainNode
                                .getNodeContract()
                                .get()
                                .getPayload()
                                .getTags()
                                .stream()
                            : Stream.of()
                        : Stream.of())
            .toList());
    return tags;
  }
}
