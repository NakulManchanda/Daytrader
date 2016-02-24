/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

/**
 * This class is a 'rule' with no primary rule but all sub-rules must pass before
 * the container says it passes. Use this container to hold sub-rules that must ALL
 * PASS TOGETHER for a test to be deemed passed.
 * @author Roy
 */
public class RulesContainer extends AbstractBaseRule {

    @Override
    protected boolean runPrimaryRule() {
        return true;
    }
    
}
