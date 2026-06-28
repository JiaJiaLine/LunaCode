/**
 * 二叉树的最大路径和
 *
 * 路径：从树中任意节点出发，沿着父子连接到达任意节点的序列。
 * 同一个节点在路径中最多出现一次，路径至少包含一个节点。
 * 求所有路径的最大和。（路径不一定经过根节点）
 *
 * 核心思路：后序遍历 + 递归
 *  - 对每个节点，计算「经过该节点」的最大路径和 = 左贡献 + 节点值 + 右贡献
 *  - 全局变量 maxSum 记录最大值
 *  - 函数返回值：该节点作为路径「一端」能提供的最大贡献值
 *    = 节点值 + max(左贡献, 右贡献)，若为负数则返回 0（舍弃负贡献）
 */
public class BinaryTreeMaxPathSum {

    private int maxSum = Integer.MIN_VALUE;

    /**
     * 计算二叉树的最大路径和
     * @param root 二叉树根节点
     * @return 最大路径和
     */
    public int maxPathSum(TreeNode root) {
        maxSum = Integer.MIN_VALUE;
        maxGain(root);
        return maxSum;
    }

    /**
     * 后序遍历：返回以 node 为起点的最大单侧贡献值
     */
    private int maxGain(TreeNode node) {
        if (node == null) {
            return 0;
        }

        // 递归计算左右子树的最大贡献值（负数则舍弃，取 0）
        int leftGain = Math.max(maxGain(node.left), 0);
        int rightGain = Math.max(maxGain(node.right), 0);

        // 经过当前节点的最大路径和
        int currentPathSum = node.val + leftGain + rightGain;

        // 更新全局最大值
        maxSum = Math.max(maxSum, currentPathSum);

        // 返回当前节点作为路径一端能提供的最大贡献
        return node.val + Math.max(leftGain, rightGain);
    }

    // ===== 测试 =====
    public static void main(String[] args) {
        // 示例 1:      1
        //            / \
        //           2   3
        // 最大路径和 = 2 + 1 + 3 = 6
        TreeNode root1 = new TreeNode(1,
                new TreeNode(2),
                new TreeNode(3));
        BinaryTreeMaxPathSum solver = new BinaryTreeMaxPathSum();
        System.out.println("示例 1: " + solver.maxPathSum(root1) + " (期望: 6)");

        // 示例 2:     -10
        //            /  \
        //           9   20
        //              /  \
        //             15   7
        // 最大路径和 = 15 + 20 + 7 = 42
        TreeNode root2 = new TreeNode(-10,
                new TreeNode(9),
                new TreeNode(20,
                        new TreeNode(15),
                        new TreeNode(7)));
        System.out.println("示例 2: " + solver.maxPathSum(root2) + " (期望: 42)");

        // 示例 3: 负值单节点  -3
        TreeNode root3 = new TreeNode(-3);
        System.out.println("示例 3: " + solver.maxPathSum(root3) + " (期望: -3)");

        // 示例 4: 全负数     -3
        //                  /   \
        //                -5    -2
        // 最大路径和 = max(-3, -5, -2) = -2
        TreeNode root4 = new TreeNode(-3,
                new TreeNode(-5),
                new TreeNode(-2));
        System.out.println("示例 4: " + solver.maxPathSum(root4) + " (期望: -2)");
    }
}

/**
 * 二叉树节点定义
 */
class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;

    TreeNode() {}
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}
