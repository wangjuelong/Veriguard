package io.veriguard.injector_contract;

import java.util.List;

/**
 * Abstract base class for implementing nodeExecutor contractors.
 *
 * <p>A Contractor is responsible for defining and providing the contracts (capabilities) of an
 * nodeExecutor. Each nodeExecutor implementation extends this class to specify:
 *
 * <ul>
 *   <li>The unique type identifier for the nodeExecutor
 *   <li>The icon displayed in the UI
 *   <li>The configuration metadata
 *   <li>The list of contracts (injection capabilities) provided
 * </ul>
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class EmailContractor extends Contractor {
 *
 *     @Override
 *     public String getType() { return "email"; }
 *
 *     @Override
 *     public List<Contract> contracts() {
 *         return List.of(sendEmailContract, phishingContract);
 *     }
 * }
 * }</pre>
 *
 * @see Contract
 * @see ContractConfig
 */
public abstract class Contractor {

  /**
   * Returns the unique type identifier for this contractor's nodeExecutor.
   *
   * @return the nodeExecutor type (e.g., "email", "sms", "caldera")
   */
  public abstract String getType();

  /**
   * Returns the icon to display for this nodeExecutor.
   *
   * @return the contractor icon, or null if no custom icon is provided
   */
  public abstract ContractorIcon getIcon();

  /**
   * Returns the configuration metadata for this contractor.
   *
   * @return the contract configuration containing display properties
   */
  public abstract ContractConfig getConfig();

  /**
   * Returns the list of contracts (injection capabilities) provided by this contractor.
   *
   * @return list of available contracts
   * @throws Exception if contract initialization fails
   */
  public abstract List<Contract> contracts() throws Exception;
}
