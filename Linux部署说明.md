# Linux服务器部署说明

## Chrome浏览器安装

### Ubuntu/Debian系统
```bash
# 安装Chrome浏览器
wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | sudo apt-key add -
echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" | sudo tee /etc/apt/sources.list.d/google-chrome.list
sudo apt update
sudo apt install google-chrome-stable

# 或者安装Chromium（开源版本）
sudo apt install chromium-browser
```

### CentOS/RHEL系统
```bash
# 安装Chrome浏览器
sudo yum install -y wget
wget https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm
sudo yum localinstall google-chrome-stable_current_x86_64.rpm

# 或者安装Chromium
sudo yum install chromium
```

### 验证安装
```bash
# 检查Chrome是否安装成功
google-chrome --version
# 或
chromium-browser --version
```

## 系统依赖安装

### 安装必要的系统库
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y \
    libnss3-dev \
    libatk-bridge2.0-dev \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libgbm1 \
    libxss1 \
    libasound2

# CentOS/RHEL
sudo yum install -y \
    nss \
    atk \
    libdrm \
    libxkbcommon \
    libXcomposite \
    libXdamage \
    libXrandr \
    mesa-libgbm \
    libXScrnSaver \
    alsa-lib
```

## 应用程序配置

### 1. 修改部署域名配置
在`application-prod.yml`中修改：
```yaml
# 部署路径
code:
  deploy-host: http://your-server-ip:8101/example/static
```

### 2. 确保端口开放
```bash
# 开放8101端口
sudo ufw allow 8101
# 或使用iptables
sudo iptables -A INPUT -p tcp --dport 8101 -j ACCEPT
```

### 3. 启动应用
```bash
# 后台启动
nohup java -jar ai-code.jar --spring.profiles.active=prod > app.log 2>&1 &
```

## 测试截图功能

### 检查Chrome路径
应用启动后，查看日志中的Chrome路径检测信息：
```
找到Chrome浏览器：/usr/bin/google-chrome
```

### 测试部署功能
1. 创建Vue项目
2. 点击部署按钮
3. 检查生成的URL是否可以访问
4. 验证截图是否正常生成

## 常见问题解决

### 1. Chrome启动失败
```bash
# 检查Chrome权限
sudo chmod +x /usr/bin/google-chrome

# 检查依赖库
ldd /usr/bin/google-chrome
```

### 2. 截图功能不工作
- 检查Chrome是否正确安装
- 查看应用日志中的Chrome初始化信息
- 确保系统有足够的内存和磁盘空间

### 3. 部署URL无法访问
- 检查防火墙设置
- 确认端口8101是否开放
- 验证静态资源控制器配置

## 性能优化建议

### 1. 内存优化
```bash
# 设置JVM参数
export JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
```

### 2. Chrome资源限制
在代码中已经添加了Linux特殊参数来优化Chrome性能：
- `--disable-background-timer-throttling`
- `--disable-backgrounding-occluded-windows`
- `--disable-renderer-backgrounding`

### 3. 系统资源监控
```bash
# 监控内存使用
free -h
# 监控磁盘使用
df -h
# 监控进程
ps aux | grep java
```
