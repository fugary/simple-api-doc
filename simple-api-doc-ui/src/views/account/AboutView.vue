<script setup>
import { GITHUB_ADDRESS } from '@/config'
</script>
<template>
  <el-container class="flex-column">
    <strong>
      基于Spring Boot开发的API文档服务
    </strong>
    <p>
      主要要两个项目:<br>
      1. 后台服务simple-api-doc<br>
      2. UI界面simple-api-doc-ui<br>
      最后打包的时候合并到一个项目中，成为一个jar包，最后打包成为一个zip文件，加压运行即可。<br>
      主要功能，提供OpenAPI文档查看服务，代替Swagger UI界面
    </p>
    <p>
      GitHub源码：<el-link
        v-if="GITHUB_ADDRESS"
        :href="GITHUB_ADDRESS"
        type="primary"
        target="_blank"
      >
        {{ GITHUB_ADDRESS }}
      </el-link>
    </p>
  </el-container>
</template>

<style>
</style>
