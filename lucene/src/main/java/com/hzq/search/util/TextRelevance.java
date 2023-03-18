package com.hzq.search.util;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 问题：
 * 1.构建过程耗时
 * 2.每次计算都要构建trie树，jvm有压力
 * 适合：
 * 1.静态词汇集合情况
 * 建议：
 * 1.直接进行字符串匹配  非常快 O(m+n)
 *
 * @Date: 2022/07/23/12:39
 * @Description:
 */
public class TextRelevance {

    //是否建立了failure表
    private Boolean failureStatesConstructed = false;

    //根结点
    private Node root;


    /**
     * @Date: 2020/4/1 13:49
     * @Description: ACTire初始化
     */
    public TextRelevance() {
        this.root = new Node(true);
    }


    /**
     * @Date: 2020/4/1 13:54
     * @Description: ACTrie节点(内部用字典树构建)
     */
    private static class Node {
        private Map<Character, Node> success;
        private List<String> emits;         //输出
        private Node failure;               //失败中转
        private Boolean isRoot = false;     //是否为根结点


        public Node() {
            success = new HashMap<>();
            emits = new ArrayList<>();
        }


        public Node(Boolean isRoot) {
            this();
            this.isRoot = isRoot;
        }


        public Node insert(Character character) {
            Node node = this.success.get(character);
            if (node == null) {
                node = new Node();
                success.put(character, node);
            }
            return node;
        }


        public void addEmit(String keyword) {
            emits.add(keyword);
        }


        public void addEmit(Collection<String> keywords) {
            emits.addAll(keywords);
        }


        /**
         * @Date: 2020/4/1 14:22
         * @Description: success跳转
         */
        public Node find(Character character) {
            return success.get(character);
        }


        /**
         * @Date: 2020/4/1 14:23
         * @Description: 状态转移(此处的transition为转移的状态 ， 可理解为接收的一个词)
         */
        private Node nextState(Character transition) {
            Node state = this.find(transition);             //先按success跳转

            if (state != null) {
                return state;
            }

            if (this.isRoot) {                              //如果跳转到根结点还是失败，则返回根结点
                return this;
            }

            return this.failure.nextState(transition);      // 跳转失败，按failure跳转
        }


        public Collection<Node> children() {
            return this.success.values();
        }


        public void setFailure(Node node) {
            failure = node;
        }


        public Node getFailure() {
            return failure;
        }


        public Set<Character> getTransitions() {
            return success.keySet();
        }


        public Collection<String> emit() {
            return this.emits == null ? Collections.<String>emptyList() : this.emits;
        }
    }

    /**
     * 命中一个模式串的处理方法
     */
    public interface IHit {
        /**
         * 命中一个模式串
         *
         * @param begin 模式串在母文本中的起始位置
         * @param end   模式串在母文本中的终止位置
         * @param emit 模式串对应的值
         */
        void hit(int begin, int end, String emit);
    }


    /**
     * @Date: 2020/4/1 15:01
     * @Description: 模式串(用于模式串匹配)
     */
    public static class Emit {
        private final String keyword;   //匹配到的模式串
        private final int start;        //起点

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        private final int end;          //终点

        public Emit(final int start, final int end, final String keyword) {
            this.start = start;
            this.end = end;
            this.keyword = keyword;
        }

        public String getKeyword() {
            return this.keyword;
        }

        @Override
        public String toString() {
            return super.toString() + "=" + this.keyword;
        }
    }


    /**
     * @Date: 2020/4/1 15:10
     * @Description: 添加一个模式串(内部使用字典树构建)
     */
    public void addKeyword(String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return;
        }

        Node currentState = this.root;
        for (Character character : keyword.toCharArray()) {
            currentState = currentState.insert(character);
        }
        currentState.addEmit(keyword);          //记录完整路径的output表(第一步)
    }


    /**
     * @Date: 2020/4/1 17:43
     * @Description: 模式匹配
     */
    public void parsetText4Pos(String text,IHit iHit) {
        text = StringTools.normalWithNotWordStr(text);
        checkForConstructedFailureStates();
        Node currentState = this.root;
        for (int position = 0; position < text.length(); position++) {
            Character character = text.charAt(position);
            currentState = currentState.nextState(character);
            Collection<String> emits = currentState.emit();
            if (emits == null || emits.isEmpty()) {
                continue;
            }
            for (String emit : emits) {
                iHit.hit(position - emit.length() + 1,position,emit);
            }
        }
    }

    /**
     * @Description 单纯获取匹配词的偏移量，并去重(防止分出重复的词造成重复)
     * @Param text 匹配串
     * @Return Map<String,List<Integer>> 模式串和对应去重后的偏移量
     * @Date 2022/11/2 15:22
     */
    public Map<String,List<Integer>> parsetText4DistinctPos(String text) {
        HashMap<String, List<Integer>> positionMap = Maps.newHashMap();
        text = StringTools.normalWithNotWordStr(text);
        checkForConstructedFailureStates();
        Node currentState = this.root;
        for (int position = 0; position < text.length(); position++) {
            Character character = text.charAt(position);
            currentState = currentState.nextState(character);
            Collection<String> emits = currentState.emit();
            if (emits == null || emits.isEmpty()) {
                continue;
            }
            for (String emit : emits) {
                List offsets = positionMap.computeIfAbsent(emit,t -> new ArrayList<>());
                Integer offset = position - emit.length() + 1;
                if(!offsets.contains(offset)){
                    offsets.add(offset);
                }
            }
        }
        return positionMap;
    }


    /**
     * @Date: 2020/4/1 16:04
     * @Description: 建立Fail表(核心, BFS遍历)
     */
    private void constructFailureStates() {
        Queue<Node> queue = new LinkedList<>();

        for (Node depthOneState : this.root.children()) {
            depthOneState.setFailure(this.root);
            queue.add(depthOneState);
        }
        this.failureStatesConstructed = true;

        while (!queue.isEmpty()) {
            Node parentNode = queue.poll();
            for (Character transition : parentNode.getTransitions()) {
                Node childNode = parentNode.find(transition);
                queue.add(childNode);
                Node failNode = parentNode.getFailure().nextState(transition);   //在这里构建failNode
                childNode.setFailure(failNode);
                childNode.addEmit(failNode.emit());                             //用路径后缀构建output表(第二步)
            }
        }
    }


    /**
     * @Date: 2020/4/1 15:28
     * @Description: 检查是否建立了Fail表(若没建立 ， 则建立)
     */
    private void checkForConstructedFailureStates() {
        if (!this.failureStatesConstructed) {
            constructFailureStates();
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        StopWatch stopWatch = new StopWatch("耗时");
        stopWatch.start();
        List<String> segments = new ArrayList<String>() {};
        segments.add("公司");
        segments.add("勇");

        //构建ac自动机
        TextRelevance trie = new TextRelevance();
        for (String token : segments) {
            if (!StringUtils.isEmpty(token)) {
                trie.addKeyword(token);
            }
        }
        Map<String,List<Position>> map = new HashMap<>();
        trie.parsetText4Pos("公勇勇",(begin,end,emit) ->map.computeIfAbsent(emit, t -> new ArrayList<>()).add(new Position(1,begin)));
        System.out.println(JSON.toJSONString(map));
        stopWatch.stop();
        System.out.println(stopWatch.prettyPrint());
    }
}
