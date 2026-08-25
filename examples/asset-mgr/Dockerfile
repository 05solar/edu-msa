# asset-mgr (C#/.NET) — 멀티스테이지 · 비루트 실행
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY asset-mgr.csproj ./
RUN dotnet restore
COPY Program.cs ./
RUN dotnet publish -c Release -o /app /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
COPY --from=build /app ./
# .NET 8 이미지에 내장된 비루트 사용자(app, uid 1654) 사용
USER app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["dotnet", "asset-mgr.dll"]
