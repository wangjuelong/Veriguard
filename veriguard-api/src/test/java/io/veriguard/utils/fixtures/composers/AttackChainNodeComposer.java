package io.veriguard.utils.fixtures.composers;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainNodeDocumentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackChainNodeComposer extends ComposerBase<AttackChainNode> {
  @Autowired private AttackChainNodeRepository attackChainNodeRepository;
  @Autowired private AttackChainNodeDocumentRepository attackChainNodeDocumentRepository;

  public class Composer extends InnerComposerBase<AttackChainNode> {
    private final AttackChainNode attackChainNode;
    private Optional<NodeContractComposer.Composer> nodeContractComposer = Optional.empty();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<EndpointComposer.Composer> endpointComposers = new ArrayList<>();
    private Optional<AttackChainNodeStatusComposer.Composer> attackChainNodeStatusComposers = Optional.empty();
    private Optional<AttackChainNodeTestStatusComposer.Composer> attackChainNodeTestStatusComposers =
        Optional.empty();
    private final List<DocumentComposer.Composer> documentComposers = new ArrayList<>();
    private final List<TeamComposer.Composer> teamComposers = new ArrayList<>();
    private final List<AssetGroupComposer.Composer> assetGroupComposers = new ArrayList<>();
    private final List<AttackChainNodeExpectationComposer.Composer> expectationComposers = new ArrayList<>();
    private final List<FindingComposer.Composer> findingComposers = new ArrayList<>();

    public Composer(AttackChainNode attackChainNode) {
      this.attackChainNode = attackChainNode;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.attackChainNode.getTags();
      tempTags.add(tagComposer.get());
      this.attackChainNode.setTags(tempTags);
      return this;
    }

    public Composer withNodeContract(
        NodeContractComposer.Composer nodeContractComposer) {
      this.nodeContractComposer = Optional.of(nodeContractComposer);
      this.attackChainNode.setNodeContract(nodeContractComposer.get());
      return this;
    }

    public Composer withDocument(DocumentComposer.Composer documentComposer) {
      this.documentComposers.add(documentComposer);
      List<AttackChainNodeDocument> tempDocs = this.attackChainNode.getDocuments();
      AttackChainNodeDocument newDoc = new AttackChainNodeDocument();
      newDoc.setDocument(documentComposer.get());
      newDoc.setAttackChainNode(this.attackChainNode);
      tempDocs.add(newDoc);
      this.attackChainNode.setDocuments(tempDocs);
      return this;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposers.add(teamComposer);
      List<Team> tempTeams = this.attackChainNode.getTeams();
      tempTeams.add(teamComposer.get());
      this.attackChainNode.setTeams(tempTeams);
      return this;
    }

    public Composer withId(String id) {
      this.attackChainNode.setId(id);
      return this;
    }

    public Composer withAttackChainNodeStatus(AttackChainNodeStatusComposer.Composer attackChainNodeStatus) {
      attackChainNodeStatusComposers = Optional.of(attackChainNodeStatus);
      attackChainNodeStatus.get().setAttackChainNode(this.attackChainNode);
      this.attackChainNode.setStatus(attackChainNodeStatus.get());
      return this;
    }

    public Composer withAttackChainNodeTestStatus(AttackChainNodeTestStatusComposer.Composer attackChainNodeTestStatus) {
      attackChainNodeTestStatusComposers = Optional.of(attackChainNodeTestStatus);
      attackChainNodeTestStatus.get().setAttackChainNode(this.attackChainNode);
      return this;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      endpointComposers.add(endpointComposer);
      List<Asset> assets = attackChainNode.getAssets();
      assets.add(endpointComposer.get());
      this.attackChainNode.setAssets(assets);
      return this;
    }

    public Composer withAssetGroup(AssetGroupComposer.Composer assetGroupComposer) {
      assetGroupComposers.add(assetGroupComposer);
      List<AssetGroup> tempAssetGroups = this.attackChainNode.getAssetGroups();
      tempAssetGroups.add(assetGroupComposer.get());
      this.attackChainNode.setAssetGroups(tempAssetGroups);
      return this;
    }

    public Composer withExpectation(AttackChainNodeExpectationComposer.Composer expectationComposer) {
      expectationComposers.add(expectationComposer);
      List<AttackChainNodeExpectation> tempExpectations = this.attackChainNode.getExpectations();
      tempExpectations.add(expectationComposer.get());
      expectationComposer.get().setAttackChainNode(this.attackChainNode);
      this.attackChainNode.setExpectations(tempExpectations);
      return this;
    }

    public Composer withDependsOn(AttackChainNode attackChainNodeParent) {
      AttackChainEdge attackChainEdge = new AttackChainEdge();
      AttackChainEdgeId attackChainEdgeId = new AttackChainEdgeId();
      attackChainEdgeId.setAttackChainNodeParent(attackChainNodeParent);
      attackChainEdgeId.setAttackChainNodeChildren(this.attackChainNode);
      attackChainEdge.setCompositeId(attackChainEdgeId);
      this.attackChainNode.setDependsOn(new ArrayList<>(List.of(attackChainEdge)));
      return this;
    }

    public Composer withFinding(FindingComposer.Composer findingComposer) {
      findingComposers.add(findingComposer);
      List<Finding> tmpFindings = this.attackChainNode.getFindings();
      tmpFindings.add(findingComposer.get());
      findingComposer.get().setAttackChainNode(this.attackChainNode);
      this.attackChainNode.setFindings(tmpFindings);
      return this;
    }

    @Override
    public Composer persist() {
      this.nodeContractComposer.ifPresent(
          composer -> {
            composer.persist();
            if (this.attackChainNode.getContent() == null) {
              this.attackChainNode.setContent(composer.getAttackChainNodeContent());
            }
          });
      assetGroupComposers.forEach(AssetGroupComposer.Composer::persist);
      endpointComposers.forEach(EndpointComposer.Composer::persist);
      tagComposers.forEach(TagComposer.Composer::persist);
      teamComposers.forEach(TeamComposer.Composer::persist);
      documentComposers.forEach(DocumentComposer.Composer::persist);
      attackChainNodeRepository.save(attackChainNode);
      attackChainNodeStatusComposers.ifPresent(AttackChainNodeStatusComposer.Composer::persist);
      expectationComposers.forEach(AttackChainNodeExpectationComposer.Composer::persist);
      findingComposers.forEach(FindingComposer.Composer::persist);
      attackChainNodeDocumentRepository.saveAll(attackChainNode.getDocuments());
      return this;
    }

    @Override
    public Composer delete() {
      attackChainNodeRepository.delete(attackChainNode);
      documentComposers.forEach(DocumentComposer.Composer::delete);
      tagComposers.forEach(TagComposer.Composer::delete);
      endpointComposers.forEach(EndpointComposer.Composer::delete);
      assetGroupComposers.forEach(AssetGroupComposer.Composer::delete);
      attackChainNodeStatusComposers.ifPresent(AttackChainNodeStatusComposer.Composer::delete);
      teamComposers.forEach(TeamComposer.Composer::delete);
      nodeContractComposer.ifPresent(NodeContractComposer.Composer::delete);
      findingComposers.forEach(FindingComposer.Composer::delete);
      expectationComposers.forEach(AttackChainNodeExpectationComposer.Composer::delete);
      return this;
    }

    @Override
    public AttackChainNode get() {
      return this.attackChainNode;
    }
  }

  public Composer forAttackChainNode(AttackChainNode attackChainNode) {
    generatedItems.add(attackChainNode);
    return new Composer(attackChainNode);
  }
}
