/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

/**
 * This 'rule' is for DEBUG use ONLY. It is placed into a rules set to ensure that
 * the rule is alway treated as FAILED
 * @author Roy
 */
public class AlwaysFailRule extends AbstractBaseRule {

    @Override
    protected boolean runPrimaryRule() {
        return false;
    }
    
}
