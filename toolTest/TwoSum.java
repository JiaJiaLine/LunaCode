/**
 * 两数之和 —— 两种实现
 * 
 * 1. 简单加法：给定两个数，返回它们的和
 * 2. LeetCode 经典题：在数组中找出两个数，使其和等于目标值
 */
public class TwoSum {

    // 解法一：两数相加
    public static int add(int a, int b) {
        return a + b;
    }

    // 解法二：数组两数之和（返回两个下标）
    public static int[] findTwoSum(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    return new int[]{i, j};
                }
            }
        }
        return new int[]{-1, -1}; // 没找到
    }

    public static void main(String[] args) {
        // 测试加法
        int x = 3, y = 7;
        System.out.println(x + " + " + y + " = " + add(x, y));

        // 测试数组两数之和
        int[] nums = {2, 7, 11, 15};
        int target = 9;
        int[] result = findTwoSum(nums, target);
        System.out.println("数组 [2, 7, 11, 15] 中和为 9 的两个数下标：[" 
                           + result[0] + ", " + result[1] + "]");
    }
}
